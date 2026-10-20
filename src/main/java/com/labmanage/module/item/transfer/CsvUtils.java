package com.labmanage.module.item.transfer;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 解析与写出（RFC 4180 手写实现，UTF-8 编码、首行带 BOM）
 * <p>
 * 解析：状态机处理引号内逗号 / 双引号 / 换行，接受 CRLF 与 LF；UTF-8 严格解码，
 * 非 UTF-8 文件返回 40000。
 * 写出：字段含 {@code , " \r \n} 时加引号并将双引号转义；以 {@code = + - @} 开头的
 * 文本加单引号前缀，防止 Excel 公式注入。
 */
public final class CsvUtils {

    private static final char BOM = '\uFEFF';

    private CsvUtils() {
    }

    /**
     * 解析 CSV 为行矩阵（首行为表头；空行保留为单空字段行，行号与物理行对齐）
     *
     * @param maxDataRows 数据行上限（不含表头），超限抛 40000
     */
    public static List<List<String>> parse(InputStream in, int maxDataRows) throws IOException {
        String text = decodeStrictUtf8(in);
        if (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean quotedFieldClosed = false;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                    } else {
                        inQuotes = false;
                        quotedFieldClosed = true;
                        i++;
                    }
                } else {
                    field.append(c);
                    i++;
                }
            } else if (c == '"' && field.length() == 0 && !quotedFieldClosed) {
                inQuotes = true;
                i++;
            } else if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
                quotedFieldClosed = false;
                i++;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                    i += 2;
                } else {
                    i++;
                }
                current.add(field.toString());
                rows.add(current);
                current = new ArrayList<>();
                field.setLength(0);
                quotedFieldClosed = false;
            } else {
                field.append(c);
                i++;
            }
        }
        if (inQuotes) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "CSV 存在未闭合的引号");
        }
        // 末行无换行符的收尾
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current);
        }
        if (rows.size() > maxDataRows + 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "单次导入最多 " + maxDataRows + " 行，请分批导入");
        }
        return rows;
    }

    /**
     * 写出 CSV（首写 UTF-8 BOM，CRLF 行尾）
     */
    public static void write(OutputStream out, List<String[]> rows) throws IOException {
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        for (String[] row : rows) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < row.length; c++) {
                if (c > 0) {
                    line.append(',');
                }
                line.append(escape(row[c]));
            }
            line.append("\r\n");
            out.write(line.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    static String escape(String value) {
        String v = value == null ? "" : value;
        // 公式注入防护：= + - @ 开头的文本加 ' 前缀
        if (!v.isEmpty() && (v.charAt(0) == '=' || v.charAt(0) == '+'
                || v.charAt(0) == '-' || v.charAt(0) == '@')) {
            v = "'" + v;
        }
        boolean needQuotes = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\r') >= 0 || v.indexOf('\n') >= 0;
        if (needQuotes) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    private static String decodeStrictUtf8(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "CSV 编码必须为 UTF-8，请使用下载的模板");
        }
    }
}
