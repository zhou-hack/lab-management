package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置用户密码请求（管理员/运维操作，重置后目标用户首登强制改密）。
 * newPassword 为前端 RSA 公钥加密后的 Base64 密文，长度按密文放宽，
 * 强度规则由服务端解密后校验（与修改密码一致）。
 */
public record UserPasswordResetDTO(

        @NotBlank(message = "新密码不能为空")
        @Size(max = 512, message = "新密码密文长度超出限制")
        String newPassword
) {
}
