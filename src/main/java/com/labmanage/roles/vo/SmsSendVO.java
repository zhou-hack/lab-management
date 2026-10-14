package com.labmanage.roles.vo;

/**
 * 发送验证码响应
 *
 * @param ttlSeconds 验证码有效期（秒）
 * @param devCode    模拟通道验证码，仅 dev 环境返回用于联调，其他环境为 null
 */
public record SmsSendVO(Integer ttlSeconds, String devCode) {
}
