package com.labmanage.module.item.util;

import com.labmanage.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ID 解析工具单元测试（接口字符串雪花 ID → Long）
 */
class IdUtilsTest {

    @Test
    void requireIdParsesValidDigits() {
        assertEquals(1234567890123456789L, IdUtils.requireId("1234567890123456789", "物料 ID"));
    }

    @Test
    void requireIdRejectsInvalidInput() {
        assertThrows(BusinessException.class, () -> IdUtils.requireId("abc", "物料 ID"));
        assertThrows(BusinessException.class, () -> IdUtils.requireId("123a", "物料 ID"));
        // 超出 Long 范围（20 位）
        assertThrows(BusinessException.class, () -> IdUtils.requireId("12345678901234567890", "物料 ID"));
    }

    @Test
    void requireIdRejectsNullAndBlank() {
        assertThrows(BusinessException.class, () -> IdUtils.requireId(null, "物料 ID"));
        assertThrows(BusinessException.class, () -> IdUtils.requireId("", "物料 ID"));
        assertThrows(BusinessException.class, () -> IdUtils.requireId("  ", "物料 ID"));
    }

    @Test
    void optionalIdReturnsNullForBlank() {
        assertNull(IdUtils.optionalId(null, "归属老师 ID"));
        assertNull(IdUtils.optionalId("", "归属老师 ID"));
        assertNull(IdUtils.optionalId("  ", "归属老师 ID"));
    }

    @Test
    void optionalIdParsesOrRejects() {
        assertEquals(456L, IdUtils.optionalId("456", "归属老师 ID"));
        assertThrows(BusinessException.class, () -> IdUtils.optionalId("x1", "归属老师 ID"));
    }
}
