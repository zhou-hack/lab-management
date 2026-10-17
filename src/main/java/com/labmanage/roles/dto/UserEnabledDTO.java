package com.labmanage.roles.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 启用/停用用户请求
 */
public record UserEnabledDTO(

        @NotNull(message = "启用状态不能为空")
        @Min(value = 0, message = "启用状态仅支持 0/1")
        @Max(value = 1, message = "启用状态仅支持 0/1")
        Integer enabled
) {
}
