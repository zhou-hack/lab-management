package com.labmanage.module.item.transfer;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;

import java.util.Locale;
import java.util.Set;

/**
 * 导入布尔列宽容解析（是/否、true/false、1/0、y/n、yes/no）
 */
public final class BooleanParser {

    private static final Set<String> TRUE_VALUES = Set.of("是", "true", "1", "y", "yes");
    private static final Set<String> FALSE_VALUES = Set.of("否", "false", "0", "n", "no");

    private BooleanParser() {
    }

    /**
     * 解析布尔值，空或未识别抛 40000
     */
    public static Boolean parse(String raw, String columnName) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    columnName + "不能为空，只能填：是 / 否");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(value)) {
            return Boolean.TRUE;
        }
        if (FALSE_VALUES.contains(value)) {
            return Boolean.FALSE;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                columnName + "只能填：是 / 否，识别到「" + raw.trim() + "」");
    }
}
