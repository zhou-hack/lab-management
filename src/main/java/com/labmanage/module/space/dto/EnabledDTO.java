package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 启用/停用请求（space 模块各资源共用）
 */
public record EnabledDTO(

        @NotNull(message = "启用状态不能为空")
        Integer enabled
) {
}
