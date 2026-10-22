package com.labmanage.roles.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

/**
 * 用户数据范围表（sys_user_data_scope，管理员归口授权）。
 * scope_target_id 为生成列不映射；campus/building/lab 目标待空间模块落地后开放，一期不映射。
 */
@TableName("sys_user_data_scope")
public class SysUserDataScope extends BaseEntity {

    /** 目标管理员（→ sys_user.id） */
    private Long userId;

    /** 范围类型：ALL/DEPARTMENT（CAMPUS/BUILDING/LAB 一期不开放） */
    private String scopeType;

    /** 目标部门（scope_type=DEPARTMENT 时必填，ALL 为 NULL） */
    private Long departmentId;

    /** 操作级别：READ/WRITE（WRITE 含 READ） */
    private String accessMode;

    private Integer enabled;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getAccessMode() {
        return accessMode;
    }

    public void setAccessMode(String accessMode) {
        this.accessMode = accessMode;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
