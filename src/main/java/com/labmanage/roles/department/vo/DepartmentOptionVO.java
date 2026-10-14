package com.labmanage.roles.department.vo;

/**
 * 部门下拉选项（登录即可用；前端按 parentId 组装树）
 */
public record DepartmentOptionVO(
        String id,
        String parentId,
        String departmentCode,
        String departmentName,
        String departmentType,
        Integer enabled
) {
}
