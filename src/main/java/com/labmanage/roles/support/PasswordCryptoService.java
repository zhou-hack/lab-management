package com.labmanage.roles.support;

import com.labmanage.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * 口令传输加密（RSA/2048 + PKCS#1 v1.5）。
 * 公钥经 GET /api/v1/auth/public_key 下发，前端加密后以 Base64 密文上传；
 * 私钥仅存服务端内存，实例重启即轮换，旧密文解密失败时前端重新取公钥即可。
 * 密文不出现在报文与日志中；传输层安全仍以 HTTPS 为准。
 */
@Component
public class PasswordCryptoService {

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    public PasswordCryptoService() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("口令加密密钥对初始化失败", e);
        }
    }

    /** X.509 公钥 Base64（供前端加密库使用） */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** 解出明文口令；密文非法（非本公钥加密、截断、篡改）统一按参数错误处理 */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new BusinessException(40000, "密码密文不能为空");
        }
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(40000, "密码密文无效，请刷新后重试");
        }
    }
}
