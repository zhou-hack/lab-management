package com.labmanage.common.utils;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;

/**
 * 雪花 ID 解析工具
 * <p>
 * 接口层 ID 统一以字符串传输（避免 JavaScript 精度损失），
 * 非法格式按资源不存在处理。
 */
public final class Ids {

    private Ids() {
    }

    public static Long parse(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "资源不存在");
        }
    }
}
