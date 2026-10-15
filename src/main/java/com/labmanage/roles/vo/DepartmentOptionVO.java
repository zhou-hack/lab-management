package com.labmanage.roles.vo;

/**
 * 部门下拉选项（parentId 供前端组装树形结构，根部门为 null）
 */
public record DepartmentOptionVO(
        String id,
        String parentId,
        String departmentName
) {
}
