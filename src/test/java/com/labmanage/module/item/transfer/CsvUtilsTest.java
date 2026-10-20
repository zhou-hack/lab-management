package com.labmanage.module.item.transfer;

import com.labmanage.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CsvUtils 单元测试：RFC 4180 解析状态机、BOM、编码校验、写出转义与公式防护
 */
class CsvUtilsTest {

    private static final int LARGE_LIMIT = 10000;

    private List<List<String>> parse(String text) throws IOException {
        return CsvUtils.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), LARGE_LIMIT);
    }

    @Test
    void parsesSimpleRows() throws IOException {
        List<List<String>> rows = parse("a,b,c\r\n1,2,3\r\n");
        assertEquals(2, rows.size());
        assertEquals(List.of("a", "b", "c"), rows.get(0));
        assertEquals(List.of("1", "2", "3"), rows.get(1));
    }

    @Test
    void acceptsLfLineEnding() throws IOException {
        List<List<String>> rows = parse("a,b\n1,2\n");
        assertEquals(2, rows.size());
        assertEquals(List.of("1", "2"), rows.get(1));
    }

    @Test
    void lastRowWithoutLineEndingIsKept() throws IOException {
        List<List<String>> rows = parse("a,b\r\n1,2");
        assertEquals(2, rows.size());
        assertEquals(List.of("1", "2"), rows.get(1));
    }

    @Test
    void parsesQuotedFieldWithCommaAndNewline() throws IOException {
        List<List<String>> rows = parse("\"a,b\",\"line1\nline2\",normal\r\n");
        assertEquals(1, rows.size());
        assertEquals("a,b", rows.get(0).get(0));
        assertEquals("line1\nline2", rows.get(0).get(1));
        assertEquals("normal", rows.get(0).get(2));
    }

    @Test
    void parsesEscapedDoubleQuoteInsideQuotes() throws IOException {
        List<List<String>> rows = parse("\"say \"\"ok\"\"\",x\r\n");
        assertEquals(List.of("say \"ok\"", "x"), rows.get(0));
    }

    @Test
    void keepsEmptyLineAsSingleEmptyFieldRow() throws IOException {
        List<List<String>> rows = parse("h1,h2\r\n\r\n1,2\r\n");
        assertEquals(3, rows.size());
        assertEquals(List.of(""), rows.get(1));
        assertEquals(List.of("1", "2"), rows.get(2));
    }

    @Test
    void stripsUtf8Bom() throws IOException {
        byte[] bomPlusText = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] text = "a,b\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bomPlusText.length + text.length];
        System.arraycopy(bomPlusText, 0, all, 0, bomPlusText.length);
        System.arraycopy(text, 0, all, bomPlusText.length, text.length);
        List<List<String>> rows = CsvUtils.parse(new ByteArrayInputStream(all), LARGE_LIMIT);
        assertEquals("a", rows.get(0).get(0));
    }

    @Test
    void rejectsUnclosedQuote() {
        assertThrows(BusinessException.class, () -> parse("\"unclosed,field\r\n"));
    }

    @Test
    void rejectsNonUtf8Content() {
        // GBK 编码的中文（"物料"）不是合法 UTF-8 序列
        byte[] gbk = new byte[]{(byte) 0xCE, (byte) 0xEF, (byte) 0xC1, (byte) 0xCF};
        BusinessException e = assertThrows(BusinessException.class,
                () -> CsvUtils.parse(new ByteArrayInputStream(gbk), LARGE_LIMIT));
        assertTrue(e.getMessage().contains("UTF-8"));
    }

    @Test
    void rejectsRowsBeyondLimit() throws IOException {
        // 表头 + 3 数据行，maxDataRows=2 应超限
        StringBuilder sb = new StringBuilder("h\r\n");
        for (int i = 0; i < 3; i++) {
            sb.append(i).append("\r\n");
        }
        BusinessException e = assertThrows(BusinessException.class, () -> CsvUtils.parse(
                new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)), 2));
        assertTrue(e.getMessage().contains("最多"));
    }

    @Test
    void allowsRowsAtLimit() throws IOException {
        StringBuilder sb = new StringBuilder("h\r\n");
        for (int i = 0; i < 2; i++) {
            sb.append(i).append("\r\n");
        }
        List<List<String>> rows = CsvUtils.parse(
                new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)), 2);
        assertEquals(3, rows.size());
    }

    @Test
    void writeThenParseRoundTrip() throws IOException {
        String[] header = {"分类编码", "SKU码", "物料名称"};
        String[] data = {"CAT-1", "SKU,\"X\"", "含\n换行"};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvUtils.write(out, List.of(header, data));
        byte[] bytes = out.toByteArray();
        // BOM 存在
        assertEquals((byte) 0xEF, bytes[0]);
        List<List<String>> parsed = CsvUtils.parse(new ByteArrayInputStream(bytes), LARGE_LIMIT);
        assertEquals(2, parsed.size());
        assertEquals(data[1], parsed.get(1).get(1));
        assertEquals(data[2], parsed.get(1).get(2));
    }

    @Test
    void writeEscapesFormulaPrefix() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String[] row = {"=SUM(A1)", "+5", "-1", "@cmd", "普通"};
        CsvUtils.write(out, java.util.Collections.singletonList(row));
        String text = new String(out.toByteArray(), 3, out.size() - 3, StandardCharsets.UTF_8);
        assertTrue(text.contains("'=SUM(A1)"));
        assertTrue(text.contains("'+5"));
        assertTrue(text.contains("'-1"));
        assertTrue(text.contains("'@cmd"));
        assertTrue(text.contains("普通"));
        // 防护不影响回读语义：前缀 ' 属于文本一部分
        List<List<String>> parsed = CsvUtils.parse(
                new ByteArrayInputStream(out.toByteArray()), LARGE_LIMIT);
        assertEquals("'=SUM(A1)", parsed.get(0).get(0));
    }

    @Test
    void parseEmptyInputReturnsEmpty() throws IOException {
        List<List<String>> rows = CsvUtils.parse(new ByteArrayInputStream(new byte[0]), LARGE_LIMIT);
        assertTrue(rows.isEmpty());
    }

    @Test
    void parsesCRLFInsideQuotedField() throws IOException {
        List<List<String>> rows = parse("\"a\r\nb\",next\r\n");
        assertEquals("a\r\nb", rows.get(0).get(0));
        assertEquals("next", rows.get(0).get(1));
    }

    @Test
    void mixedLineEndingsDoNotThrow() {
        assertDoesNotThrow(() -> parse("a,b\rc,d\r\ne,f\n"));
    }
}
