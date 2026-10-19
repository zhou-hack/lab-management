package com.labmanage.module.item.transfer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * CSV 行写出器（UTF-8 BOM + RFC 4180 转义 + 公式注入防护，委托 CsvUtils.escape）
 */
public class CsvRowWriter implements RowWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public CsvRowWriter() throws IOException {
        // 首写 UTF-8 BOM，Excel 直接打开不乱码
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
    }

    @Override
    public void writeRow(String... cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int c = 0; c < cells.length; c++) {
            if (c > 0) {
                line.append(',');
            }
            line.append(CsvUtils.escape(cells[c]));
        }
        line.append("\r\n");
        out.write(line.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public byte[] toBytes() {
        return out.toByteArray();
    }

    @Override
    public void close() {
        // ByteArrayOutputStream 无需释放
    }
}
