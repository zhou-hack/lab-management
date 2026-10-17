package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 校区创建请求
 */
public record CampusCreateDTO(

        @NotBlank(message = "校区编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "校区编码需为 1-64 位字母、数字或下划线")
        String campusCode,

        @NotBlank(message = "校区名称不能为空")
        @Size(max = 128, message = "校区名称长度不能超过 128")
        String campusName,

        @Size(max = 255, message = "地址长度不能超过 255")
        String address
) {
}
