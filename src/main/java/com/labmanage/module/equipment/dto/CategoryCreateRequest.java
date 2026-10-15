package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 设备分类新增请求
 */
public record CategoryCreateRequest(
        /** 父分类（NULL=根节点） */
        Long parentId,

        @NotBlank(message = "分类编码不能为空")
        @Size(max = 64, message = "分类编码长度不能超过 64")
        String categoryCode,

        @NotBlank(message = "分类名称不能为空")
        @Size(max = 128, message = "分类名称长度不能超过 128")
        String categoryName
) {
}
