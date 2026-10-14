package com.labmanage.roles.service;

/**
 * 短信验证码服务
 */
public interface SmsCodeService {

    /**
     * 发送验证码（一期模拟通道，验证码写入应用日志）。
     *
     * @param phone 手机号
     * @return 验证码有效期（秒）
     */
    int send(String phone);

    /**
     * 校验并消费验证码；错误、失效或超次抛出业务异常。
     */
    void verify(String phone, String code);
}
