package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 校区修改请求（编码不可改，删除后不复用）
 */
public record CampusUpdateRequest(
        @NotBlank(message = "校区名称不能为空")
        @Size(max = 128, message = "校区名称长度不能超过 128")
        String campusName,

        @Size(max = 255, message = "校区地址长度不能超过 255")
        String address,

        @NotNull(message = "启用状态不能为空")
        Integer enabled
) {
}
