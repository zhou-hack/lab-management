package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 名单录入请求（单条）
 */
public record RosterCreateDTO(

        @NotBlank(message = "学工号不能为空")
        @Pattern(regexp = "^[A-Za-z0-9]{2,32}$", message = "学工号需为 2-32 位字母或数字")
        String personNo,

        @NotBlank(message = "身份类型不能为空")
        @Pattern(regexp = "STUDENT|TEACHER", message = "身份类型仅支持 STUDENT/TEACHER")
        String personType,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 128, message = "姓名长度不能超过 128")
        String realName,

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        String phone,

        @NotNull(message = "所属部门不能为空")
        Long departmentId
) {
}
