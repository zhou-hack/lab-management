package com.labmanage.module.item.util;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;

/**
 * 模块内部 ID 解析工具（接口以字符串传输雪花 ID，服务端统一解析校验）
 */
public final class IdUtils {

    private IdUtils() {
    }

    /**
     * 解析必填 ID，格式非法返回 40000
     */
    public static Long requireId(String raw, String fieldName) {
        Long id = optionalId(raw, fieldName);
        if (id == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), fieldName + "不能为空或格式无效");
        }
        return id;
    }

    /**
     * 解析可空 ID，空返回 null
     */
    public static Long optionalId(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), fieldName + "格式无效");
        }
    }
}
