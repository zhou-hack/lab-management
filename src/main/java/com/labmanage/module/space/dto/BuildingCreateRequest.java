package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 楼栋新增请求
 */
public record BuildingCreateRequest(
        @NotNull(message = "所属校区不能为空")
        Long campusId,

        @NotBlank(message = "楼栋编码不能为空")
        @Size(max = 64, message = "楼栋编码长度不能超过 64")
        String buildingCode,

        @NotBlank(message = "楼栋名称不能为空")
        @Size(max = 128, message = "楼栋名称长度不能超过 128")
        String buildingName
) {
}
