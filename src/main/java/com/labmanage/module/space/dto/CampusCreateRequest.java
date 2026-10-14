package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 校区新增请求
 */
public record CampusCreateRequest(
        @NotBlank(message = "校区编码不能为空")
        @Size(max = 64, message = "校区编码长度不能超过 64")
        String campusCode,

        @NotBlank(message = "校区名称不能为空")
        @Size(max = 128, message = "校区名称长度不能超过 128")
        String campusName,

        @Size(max = 255, message = "校区地址长度不能超过 255")
        String address
) {
}
