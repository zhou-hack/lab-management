package com.labmanage.module.space.service.support;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场地导入文件解析：CSV（UTF-8，RFC 4180 引号规则，兼容导出的「~」时段与 BOM）
 * 与 Excel（.xls/.xlsx）统一解析为「规范中文表头 → 单元格文本」的行集合。
 * <p>
 * 文件级问题（空文件、格式损坏、表头缺失、超行数）抛 {@link BusinessException}（40000）；
 * 行级数据合法性由 Service 校验后逐条返回，不在此处拦截。
 */
@Component
public class SpaceImportFileParser {

    /** 规范表头（顺序与 CSV 导出完全一致，保证导出文件可直接导入） */
    public static final List<String> HEADERS = List.of(
            "场地名称", "类型", "位置", "容纳人数", "开放时段", "状态", "配套设备",
            "创建时间(上海)", "更新时间(上海)");

    /** 必填列：缺失即整文件拒收 */
    public static final List<String> REQUIRED_HEADERS = List.of("场地名称", "类型", "容纳人数");

    /** 单次导入数据行上限（不含表头与全空行），防超大文件拖垮服务 */
    public static final int MAX_DATA_ROWS = 5000;

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** 解析后的一行：rowNum 为表格实际行号（表头第 1 行，首条数据为 2） */
    public record RawRow(int rowNum, Map<String, String> cells) {
    }

    /** 文件级问题统一归类为参数错误（HTTP 400 / code=40000） */
    private static BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 解析上传文件。
     *
     * @return 非空数据行（全空行跳过），cells 只包含命中规范表头的列
     */
    public List<RawRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("导入文件为空，请上传 CSV 或 Excel 文件");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw badRequest("导入文件读取失败，请重新上传");
        }
        if (isZip(bytes)) {
            return parseExcel(bytes);
        }
        if (isOle2(bytes)) {
            return parseExcel(bytes);
        }
        // 其余一律按 CSV（UTF-8）文本处理；扩展名仅用于错误提示
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".csv") && !name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw badRequest("文件格式不支持，请上传 .csv、.xlsx 或 .xls 文件");
        }
        return parseCsv(bytes);
    }

    // ---------------------------------------------------------------- Excel

    private List<RawRow> parseExcel(byte[] bytes) {
        List<RawRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw badRequest("导入文件没有任何内容，请使用最新导入模板");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<Integer, String> headerIndex = mapHeader(readExcelHeader(headerRow, formatter));

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int dataRows = 0;
            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                Map<String, String> cells = readExcelRow(row, headerIndex, formatter, evaluator);
                if (cells == null) {
                    continue;
                }
                if (isAllBlank(cells)) {
                    continue;
                }
                rows.add(new RawRow(r + 1, cells));
                if (++dataRows > MAX_DATA_ROWS) {
                    throw badRequest("单次最多导入 " + MAX_DATA_ROWS + " 行数据，请拆分后分批上传");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw badRequest("Excel 文件解析失败（文件可能已损坏或受密码保护），请检查后重新上传");
        }
        if (rows.isEmpty()) {
            throw badRequest("导入文件没有数据行，请按模板填写场地信息后上传");
        }
        return rows;
    }

    private List<String> readExcelHeader(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw badRequest("导入文件缺少表头，请使用最新导入模板");
        }
        List<String> headers = new ArrayList<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            headers.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return headers;
    }

    private Map<String, String> readExcelRow(Row row, Map<Integer, String> headerIndex,
                                             DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return null;
        }
        Map<String, String> cells = new LinkedHashMap<>();
        for (String h : HEADERS) {
            cells.put(h, "");
        }
        for (Map.Entry<Integer, String> e : headerIndex.entrySet()) {
            Cell cell = row.getCell(e.getKey());
            cells.put(e.getValue(), cellText(cell, formatter, evaluator));
        }
        return cells;
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        // Excel 中把「开放时段」单元格设成了时间格式时，按 HH:mm 输出，避免出现 0.333333
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            double value = cell.getNumericCellValue();
            if (value >= 0 && value < 1) {
                int secondsOfDay = (int) Math.round(value * 24 * 60 * 60);
                // B20：23:59:59.6 这类临界值四舍五入后可能得到 86400，越界会导致
                // LocalTime.ofSecondOfDay 抛异常；钳制到当天最后一秒
                if (secondsOfDay >= 24 * 60 * 60) {
                    secondsOfDay = 24 * 60 * 60 - 1;
                }
                return LocalTime.ofSecondOfDay(secondsOfDay).format(HH_MM);
            }
        }
        String text = evaluator == null
                ? formatter.formatCellValue(cell)
                : formatter.formatCellValue(cell, evaluator);
        return text == null ? "" : text.trim();
    }

    // ------------------------------------------------------------------ CSV

    private List<RawRow> parseCsv(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\ufeff') {
            content = content.substring(1);
        }
        List<List<String>> lines = tokenizeCsv(content);
        if (lines.isEmpty()) {
            throw badRequest("导入文件没有任何内容，请使用最新导入模板");
        }
        Map<Integer, String> headerIndex = mapHeader(lines.get(0).stream().map(String::trim).toList());

        List<RawRow> rows = new ArrayList<>();
        int dataRows = 0;
        for (int i = 1; i < lines.size(); i++) {
            List<String> line = lines.get(i);
            Map<String, String> cells = new LinkedHashMap<>();
            for (String h : HEADERS) {
                cells.put(h, "");
            }
            for (Map.Entry<Integer, String> e : headerIndex.entrySet()) {
                String value = e.getKey() < line.size() ? line.get(e.getKey()).trim() : "";
                cells.put(e.getValue(), value);
            }
            if (isAllBlank(cells)) {
                continue;
            }
            rows.add(new RawRow(i + 1, cells));
            if (++dataRows > MAX_DATA_ROWS) {
                throw badRequest("单次最多导入 " + MAX_DATA_ROWS + " 行数据，请拆分后分批上传");
            }
        }
        if (rows.isEmpty()) {
            throw badRequest("导入文件没有数据行，请按模板填写场地信息后上传");
        }
        return rows;
    }

    /** RFC 4180 风格分词：支持双引号包裹、"" 转义、字段内换行与 CRLF/LF。 */
    private List<List<String>> tokenizeCsv(String content) {
        List<List<String>> lines = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"' && !fieldStarted) {
                inQuotes = true;
                fieldStarted = true;
                continue;
            }
            if (ch == ',') {
                current.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                continue;
            }
            if (ch == '\n') {
                current.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                lines.add(current);
                current = new ArrayList<>();
                continue;
            }
            if (ch == '\r') {
                // CRLF：吃掉 \r，由后续 \n 收尾；孤立 \r 也视作换行
                current.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                lines.add(current);
                current = new ArrayList<>();
                continue;
            }
            field.append(ch);
            fieldStarted = true;
        }
        // 最后一行（无结尾换行）
        if (field.length() > 0 || !current.isEmpty() || fieldStarted) {
            current.add(field.toString());
            lines.add(current);
        }
        return lines;
    }

    // -------------------------------------------------------------- 共用

    /** 表头行 → 列序号映射（只保留命中规范表头的列），并校验必填列齐全 */
    private Map<Integer, String> mapHeader(List<String> headers) {
        Map<Integer, String> index = new LinkedHashMap<>();
        for (int c = 0; c < headers.size(); c++) {
            String h = headers.get(c) == null ? "" : headers.get(c).trim();
            if (HEADERS.contains(h)) {
                index.put(c, h);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_HEADERS) {
            if (!index.containsValue(required)) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw badRequest("导入文件缺少必填表头列：" + String.join("、", missing)
                    + "；请下载最新导入模板填写");
        }
        return index;
    }

    private boolean isAllBlank(Map<String, String> cells) {
        for (String v : cells.values()) {
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** xlsx 等 OOXML 文档为 ZIP 容器：魔数 PK\x03\x04 */
    private boolean isZip(byte[] b) {
        return b.length >= 4 && (b[0] & 0xFF) == 0x50 && (b[1] & 0xFF) == 0x4B
                && (b[2] & 0xFF) == 0x03 && (b[3] & 0xFF) == 0x04;
    }

    /** xls 旧版 OLE2 复合文档：魔数 D0 CF 11 E0 */
    private boolean isOle2(byte[] b) {
        return b.length >= 4 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF
                && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0xE0;
    }
}
