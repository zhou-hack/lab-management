package com.labmanage.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一返回体
 *
 * @param code 状态码：成功 0，失败见错误码枚举
 * @param msg  提示信息
 * @param data 数据体，失败时为 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(Integer code, String msg, T data) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);
    }

    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}
