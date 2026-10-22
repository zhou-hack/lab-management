package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建管理员账号请求。
 * 学生/老师账号必须经预录名单首登建档，不允许管理员直接创建（RBAC 定稿规则）。
 */
public record UserCreateDTO(

        @NotBlank(message = "登录账号不能为空")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$", message = "登录账号需字母开头，3-32 位字母、数字或下划线")
        String accountName,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 128, message = "姓名长度不能超过 128")
        String realName,

        @NotBlank(message = "初始密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "初始密码需 8-64 位且同时包含字母和数字")
        String password,

        @NotNull(message = "所属部门不能为空")
        Long departmentId
) {
}
