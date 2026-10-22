package com.labmanage.roles.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量修改部门（统一人员视图，名单行与账号行可混合）
 *
 * @param items        行标识列表：userId/rosterId 至少其一，两者都有则两侧同步
 * @param departmentId 目标部门
 */
public record PersonBatchDepartmentDTO(
        @NotEmpty(message = "请选择人员")
        @Size(max = 100, message = "单次最多选择 100 人")
        List<@Valid Item> items,

        @NotNull(message = "请选择目标部门")
        Long departmentId
) {
    public record Item(Long userId, Long rosterId) {
    }
}
