package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 编辑用户资料请求（账号、学工号、用户类型不可改）
 */
public record UserUpdateDTO(

        @NotBlank(message = "姓名不能为空")
        @Size(max = 128, message = "姓名长度不能超过 128")
        String realName,

        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        String phone,

        @NotNull(message = "所属部门不能为空")
        Long departmentId
) {
}
