package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 设置/修改密码请求
 * 学生/老师首次设置（无密码散列）免旧密码；其余必须提供旧密码。
 */
public record PasswordUpdateDTO(

        @Size(max = 64, message = "旧密码长度不能超过 64")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "新密码需 8-64 位且同时包含字母和数字")
        String newPassword
) {
}
