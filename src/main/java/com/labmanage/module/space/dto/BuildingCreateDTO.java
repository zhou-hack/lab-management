package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 楼栋创建请求
 */
public record BuildingCreateDTO(

        @NotNull(message = "所属校区不能为空")
        Long campusId,

        @NotBlank(message = "楼栋编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{1,64}$", message = "楼栋编码需为 1-64 位字母、数字或下划线")
        String buildingCode,

        @NotBlank(message = "楼栋名称不能为空")
        @Size(max = 128, message = "楼栋名称长度不能超过 128")
        String buildingName
) {
}
