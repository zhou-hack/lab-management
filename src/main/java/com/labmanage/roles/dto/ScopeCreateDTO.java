package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 数据范围授权请求（ALL 不填目标部门；DEPARTMENT 必填；同目标升降级通过增删行实现）
 */
public record ScopeCreateDTO(

        @NotNull(message = "目标管理员不能为空")
        Long userId,

        @NotBlank(message = "范围类型不能为空")
        @Pattern(regexp = "^(ALL|DEPARTMENT)$", message = "一期仅开放 ALL/DEPARTMENT 范围类型")
        String scopeType,

        /** scopeType=DEPARTMENT 时必填，ALL 必须为 NULL */
        Long departmentId,

        @NotBlank(message = "操作级别不能为空")
        @Pattern(regexp = "^(READ|WRITE)$", message = "操作级别仅支持 READ/WRITE")
        String accessMode
) {
}
