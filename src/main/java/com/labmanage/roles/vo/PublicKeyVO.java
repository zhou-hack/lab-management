package com.labmanage.roles.vo;

/**
 * 口令传输加密公钥（X.509 Base64，单次登录流程使用）
 */
public record PublicKeyVO(
        String publicKey
) {
}
