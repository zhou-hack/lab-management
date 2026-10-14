package com.labmanage.roles.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

/**
 * 用户实体（只读支撑：登录与 auth 模块落地前，业务模块仅查询用户基础信息）
 */
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 名单 ID（→ sys_person_roster.id；管理员为 NULL） */
    private Long rosterId;

    /** 用户类型：STUDENT/TEACHER/ADMIN */
    private String userType;

    /** 姓名 */
    private String realName;

    /** 登录账号（管理员必填；唯一） */
    private String accountName;

    /** 手机号（学生/老师必填；唯一） */
    private String phone;

    /** 学工号（唯一，删除后不复用） */
    private String personNo;

    /** 所属部门（→ sys_department.id） */
    private Long departmentId;

    /** 密码散列（不对外暴露） */
    private String passwordHash;

    /** 最近改密时间（UTC，90 天强制更换依据） */
    private Instant passwordChangedAt;

    /** 连续登录失败次数（达 5 次锁定 30 分钟） */
    private Integer failedLoginCount;

    /** 锁定截止时间（UTC） */
    private Instant lockedUntil;

    /** 启用：1正常 0停用 */
    private Integer enabled;

    public Long getRosterId() {
        return rosterId;
    }

    public void setRosterId(Long rosterId) {
        this.rosterId = rosterId;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPersonNo() {
        return personNo;
    }

    public void setPersonNo(String personNo) {
        this.personNo = personNo;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(Instant passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public Integer getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(Integer failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
