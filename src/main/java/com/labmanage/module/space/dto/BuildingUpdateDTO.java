package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 楼栋编辑请求（编码不可改；允许调整所属校区）
 */
public record BuildingUpdateDTO(

        @NotNull(message = "所属校区不能为空")
        Long campusId,

        @NotBlank(message = "楼栋名称不能为空")
        @Size(max = 128, message = "楼栋名称长度不能超过 128")
        String buildingName
) {
}
