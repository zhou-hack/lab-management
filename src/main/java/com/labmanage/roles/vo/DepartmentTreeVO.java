package com.labmanage.roles.vo;

import java.util.List;

/**
 * 部门树节点
 */
public record DepartmentTreeVO(
        String id,
        String parentId,
        String departmentCode,
        String departmentName,
        String departmentType,
        Integer enabled,
        List<DepartmentTreeVO> children
) {
}
