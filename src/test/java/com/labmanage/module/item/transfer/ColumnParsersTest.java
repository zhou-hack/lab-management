package com.labmanage.module.item.transfer;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.enums.MaterialType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入列解析器单元测试：布尔宽容解析、物料类型中英文解析、模板表头校验
 */
class ColumnParsersTest {

    // ---------- BooleanParser ----------

    @ParameterizedTest
    @ValueSource(strings = {"是", "true", "TRUE", "1", "y", "Y", "yes", "Yes", " 是 "})
    void booleanParserAcceptsTrueValues(String raw) {
        assertEquals(Boolean.TRUE, BooleanParser.parse(raw, "危险品"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"否", "false", "FALSE", "0", "n", "N", "no", "No", " 否 "})
    void booleanParserAcceptsFalseValues(String raw) {
        assertEquals(Boolean.FALSE, BooleanParser.parse(raw, "高价值"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"对", "2", "", "  ", "maybe"})
    void booleanParserRejectsInvalidValues(String raw) {
        assertThrows(BusinessException.class, () -> BooleanParser.parse(raw, "危险品"));
    }

    @Test
    void booleanParserRejectsNull() {
        assertThrows(BusinessException.class, () -> BooleanParser.parse(null, "危险品"));
    }

    @Test
    void booleanParserMessageContainsColumnName() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> BooleanParser.parse("xx", "危险品"));
        assertTrue(e.getMessage().contains("危险品"));
    }

    // ---------- MaterialTypeParser ----------

    @Test
    void materialTypeParserAcceptsChineseNames() {
        assertEquals(MaterialType.CONSUMABLE, MaterialTypeParser.parse("耗材"));
        assertEquals(MaterialType.RETURNABLE, MaterialTypeParser.parse("可回收"));
    }

    @ParameterizedTest
    @CsvSource({"CONSUMABLE", "consumable", " Consumable "})
    void materialTypeParserAcceptsEnumNames(String raw) {
        assertEquals(MaterialType.CONSUMABLE, MaterialTypeParser.parse(raw));
    }

    @Test
    void materialTypeParserRejectsInvalid() {
        assertThrows(BusinessException.class, () -> MaterialTypeParser.parse("易耗品"));
        assertThrows(BusinessException.class, () -> MaterialTypeParser.parse(""));
        assertThrows(BusinessException.class, () -> MaterialTypeParser.parse(null));
    }

    // ---------- ImportColumns.checkHeader ----------

    @Test
    void checkHeaderPassesOnExactMatch() {
        ImportColumns.checkHeader(ImportColumns.SKU_HEADER, ImportColumns.SKU_HEADER);
        ImportColumns.checkHeader(ImportColumns.CATEGORY_HEADER, ImportColumns.CATEGORY_HEADER);
    }

    @Test
    void checkHeaderAllowsExtraTailColumns() {
        ImportColumns.checkHeader(
                java.util.stream.Stream.concat(ImportColumns.SKU_HEADER.stream(), java.util.stream.Stream.of("附加列"))
                        .toList(),
                ImportColumns.SKU_HEADER);
    }

    @Test
    void checkHeaderAllowsSurroundingWhitespace() {
        ImportColumns.checkHeader(
                ImportColumns.SKU_HEADER.stream().map(c -> " " + c + " ").toList(),
                ImportColumns.SKU_HEADER);
    }

    @Test
    void checkHeaderRejectsMismatch() {
        BusinessException e = assertThrows(BusinessException.class, () ->
                ImportColumns.checkHeader(
                        List.of("编码", "SKU", "名称", "规格", "单位", "精度", "类型", "危", "贵"),
                        ImportColumns.SKU_HEADER));
        assertTrue(e.getMessage().contains("表头"));
    }

    @Test
    void checkHeaderRejectsNullAndEmpty() {
        assertThrows(BusinessException.class, () -> ImportColumns.checkHeader(null, ImportColumns.SKU_HEADER));
        assertThrows(BusinessException.class, () -> ImportColumns.checkHeader(List.of(), ImportColumns.SKU_HEADER));
    }

    @Test
    void checkHeaderRejectsTooFewColumns() {
        assertThrows(BusinessException.class, () ->
                ImportColumns.checkHeader(List.of("分类编码", "SKU码"), ImportColumns.SKU_HEADER));
    }

    @Test
    void headerSizeMatchesDesign() {
        assertEquals(9, ImportColumns.SKU_HEADER.size());
        assertEquals(3, ImportColumns.CATEGORY_HEADER.size());
        assertFalse(ImportColumns.SKU_HEADER.isEmpty());
        assertTrue(ImportColumns.SKU_HEADER.contains("高价值"));
    }
}
