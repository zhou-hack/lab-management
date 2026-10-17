package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 校区编辑请求（编码不可改）
 */
public record CampusUpdateDTO(

        @NotBlank(message = "校区名称不能为空")
        @Size(max = 128, message = "校区名称长度不能超过 128")
        String campusName,

        @Size(max = 255, message = "地址长度不能超过 255")
        String address
) {
}
