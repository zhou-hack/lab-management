package com.labmanage.common.exception;

import com.labmanage.common.result.ResultCode;

import java.io.Serial;

/**
 * 业务异常
 */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.CONFLICT.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public Integer getCode() {
        return code;
    }
}
