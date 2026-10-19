package com.labmanage.module.item.transfer;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.item.enums.MaterialType;

import java.util.Locale;

/**
 * 导入物料类型列解析（支持中文名与枚举名）
 */
public final class MaterialTypeParser {

    private MaterialTypeParser() {
    }

    /**
     * 解析物料类型，未识别抛 40000
     */
    public static MaterialType parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("consumable") || value.equals("耗材")) {
            return MaterialType.CONSUMABLE;
        }
        if (value.equals("returnable") || value.equals("可回收")) {
            return MaterialType.RETURNABLE;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                "物料类型只能填：耗材 / 可回收，识别到「" + (raw == null ? "" : raw.trim()) + "」");
    }
}
