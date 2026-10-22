package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 部门编辑请求（部门编码不可改）
 */
public record DepartmentUpdateDTO(

        @NotBlank(message = "部门名称不能为空")
        @Size(max = 128, message = "部门名称长度不能超过 128")
        String departmentName,

        /** 父部门 ID；NULL 表示根节点；禁止指向自身或自身后代 */
        Long parentId,

        @NotBlank(message = "部门类型不能为空")
        @Pattern(regexp = "SCHOOL|COLLEGE|ASSET_OFFICE", message = "部门类型仅支持 SCHOOL/COLLEGE/ASSET_OFFICE")
        String departmentType
) {
}
