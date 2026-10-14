package com.labmanage.roles.vo;

/**
 * 发送验证码响应
 *
 * @param ttlSeconds 验证码有效期（秒）
 */
public record SmsSendVO(Integer ttlSeconds) {
}
