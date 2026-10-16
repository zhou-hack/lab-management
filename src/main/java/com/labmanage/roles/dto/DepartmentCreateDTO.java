package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 部门新增请求
 */
public record DepartmentCreateDTO(

        @NotBlank(message = "部门编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{2,64}$", message = "部门编码需为 2-64 位字母、数字或下划线")
        String departmentCode,

        @NotBlank(message = "部门名称不能为空")
        @Size(max = 128, message = "部门名称长度不能超过 128")
        String departmentName,

        /** 父部门 ID；NULL 表示根节点 */
        Long parentId,

        @NotBlank(message = "部门类型不能为空")
        @Pattern(regexp = "SCHOOL|COLLEGE|ASSET_OFFICE", message = "部门类型仅支持 SCHOOL/COLLEGE/ASSET_OFFICE")
        String departmentType
) {
}
