package com.labmanage.module.item.transfer;

import com.labmanage.common.exception.BusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * xlsx / csv 行读取与写出单元测试：统一字符串矩阵语义、xlsx 文件形态（PK 魔数）、
 * 空行保留、空文件拒绝、超行数上限
 */
class RowReaderWriterTest {

    // ---------- XlsxRowReader ----------

    private static byte[] buildXlsx(Object[][] grid) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("数据");
            for (int r = 0; r < grid.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < grid[r].length; c++) {
                    Object value = grid[r][c];
                    var cell = row.createCell(c);
                    if (value instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void xlsxReaderParsesNumericCellsAsDisplayText() throws IOException {
        byte[] bytes = buildXlsx(new Object[][]{
                {"分类编码", "数量精度"},
                {"CAT-1", 3},
                {"CAT-2", 0.5}
        });
        List<List<String>> rows = new XlsxRowReader().read(new ByteArrayInputStream(bytes), 100);
        assertEquals(3, rows.size());
        assertEquals("3", rows.get(1).get(1));
        assertEquals("0.5", rows.get(2).get(1));
    }

    @Test
    void xlsxReaderReadsMissingCellAsEmpty() throws IOException {
        byte[] bytes = buildXlsx(new Object[][]{
                {"a", "b"},
                {"x"}
        });
        List<List<String>> rows = new XlsxRowReader().read(new ByteArrayInputStream(bytes), 100);
        assertEquals("x", rows.get(1).get(0));
        assertEquals("", rows.get(1).get(1));
    }

    @Test
    void xlsxReaderRejectsEmptyWorkbook() throws IOException {
        byte[] bytes = buildXlsx(new Object[][]{});
        BusinessException e = assertThrows(BusinessException.class,
                () -> new XlsxRowReader().read(new ByteArrayInputStream(bytes), 100));
        assertTrue(e.getMessage().contains("为空"));
    }

    @Test
    void xlsxReaderRejectsRowsBeyondLimit() throws IOException {
        byte[] bytes = buildXlsx(new Object[][]{
                {"h"},
                {"1"}, {"2"}, {"3"}
        });
        assertThrows(BusinessException.class,
                () -> new XlsxRowReader().read(new ByteArrayInputStream(bytes), 2));
    }

    // ---------- XlsxRowWriter ----------

    @Test
    void xlsxWriterProducesZipMagicAndReadableContent() throws IOException {
        XlsxRowWriter writer = new XlsxRowWriter();
        writer.writeRow("分类编码", "SKU码", "物料名称");
        writer.writeRow("CAT-1", "SKU-001", "无水乙醇");
        byte[] bytes = writer.toBytes();
        // xlsx 即 zip，PK 魔数
        assertEquals((byte) 0x50, bytes[0]);
        assertEquals((byte) 0x4B, bytes[1]);
        // 写出的文件可被 XlsxRowReader 读回
        List<List<String>> rows = new XlsxRowReader().read(new ByteArrayInputStream(bytes), 100);
        assertEquals(List.of("CAT-1", "SKU-001", "无水乙醇"), rows.get(1));
        writer.close();
    }

    @Test
    void xlsxWriterToBytesIsRepeatableUntilClose() throws IOException {
        XlsxRowWriter writer = new XlsxRowWriter();
        writer.writeRow("a", "b");
        byte[] first = writer.toBytes();
        assertArrayEquals(first, writer.toBytes());
        writer.close();
    }

    // ---------- CsvRowReader / CsvRowWriter ----------

    @Test
    void csvRoundTripThroughReaderAndWriter() throws IOException {
        CsvRowWriter writer = new CsvRowWriter();
        writer.writeRow("分类编码", "SKU码", "物料名称");
        writer.writeRow("CAT-1", "SKU-001", "无水乙醇");
        byte[] bytes = writer.toBytes();
        assertEquals((byte) 0xEF, bytes[0]);
        List<List<String>> rows = new CsvRowReader().read(new ByteArrayInputStream(bytes), 100);
        assertEquals(List.of("CAT-1", "SKU-001", "无水乙醇"), rows.get(1));
        writer.close();
    }

    @Test
    void csvReaderKeepsEmptyLinesForRowIndexAlignment() throws IOException {
        String csv = "h1,h2\r\n\r\nv1,v2\r\n";
        List<List<String>> rows = new CsvRowReader().read(
                new ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 100);
        assertEquals(3, rows.size());
        assertEquals(List.of(""), rows.get(1));
    }
}
