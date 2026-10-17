package com.labmanage.common.result;

/**
 * 标准错误码枚举
 * <p>
 * 40000 - 参数错误 / 400
 * 40100 - 未认证 / 401
 * 40101 - 需要二级认证（敏感操作二次密码确认）/ 401
 * 40300 - 无权限 / 403
 * 40400 - 资源不存在 / 404
 * 40900 - 业务冲突 / 409
 * 50000 - 服务器内部错误 / 500
 */
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(40000, "参数错误"),
    UNAUTHORIZED(40100, "未认证或登录已过期"),
    SAFE_AUTH_REQUIRED(40101, "敏感操作需要二次密码确认"),
    FORBIDDEN(40300, "没有操作权限"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "业务冲突"),
    INTERNAL_ERROR(50000, "服务器内部错误");

    private final Integer code;
    private final String msg;

    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
