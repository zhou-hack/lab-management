package com.labmanage.roles.department.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

/**
 * 组织实体（只读支撑：组织维护属后续基础数据模块）
 */
@TableName("sys_department")
public class SysDepartment extends BaseEntity {

    /** 父部门 ID（NULL=根节点，禁止组织循环由服务端校验） */
    private Long parentId;

    /** 部门编码 */
    private String departmentCode;

    /** 部门名称 */
    private String departmentName;

    /** 类型：SCHOOL/COLLEGE/ASSET_OFFICE */
    private String departmentType;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getDepartmentType() {
        return departmentType;
    }

    public void setDepartmentType(String departmentType) {
        this.departmentType = departmentType;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
