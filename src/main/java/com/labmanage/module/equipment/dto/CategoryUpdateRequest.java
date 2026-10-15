package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 设备分类修改请求（编码不可改，禁止循环由服务端校验）
 */
public record CategoryUpdateRequest(
        /** 父分类（NULL=根节点） */
        Long parentId,

        @NotBlank(message = "分类名称不能为空")
        @Size(max = 128, message = "分类名称长度不能超过 128")
        String categoryName,

        @NotNull(message = "启用状态不能为空")
        Integer enabled
) {
}
