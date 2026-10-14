package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 楼栋修改请求（编码不可改，删除后不复用）
 */
public record BuildingUpdateRequest(
        @NotBlank(message = "楼栋名称不能为空")
        @Size(max = 128, message = "楼栋名称长度不能超过 128")
        String buildingName,

        @NotNull(message = "启用状态不能为空")
        Integer enabled
) {
}
