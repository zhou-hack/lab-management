package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 设置/修改密码请求（口令字段均为 RSA 密文，解密后由服务端校验强度）。
 * 学生/老师首次设置（无密码散列）免旧密码；其余必须提供旧密码。
 */
public record PasswordUpdateDTO(

        @Size(max = 512, message = "旧密码密文长度超限")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(max = 512, message = "新密码密文长度超限")
        String newPassword
) {
}
