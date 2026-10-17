package com.labmanage.roles.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

/**
 * 操作日志表（sys_operation_log）
 * 只追加：不提供业务更新/删除；before_value/after_value 一期预留；
 * 密码、验证码、token 等敏感值不记录。
 */
@TableName("sys_operation_log")
public class SysOperationLog extends BaseEntity {

    /** 操作者（系统任务/匿名可空） */
    private Long actorId;

    /** 操作者类型：STUDENT/TEACHER/ADMIN/SYSTEM/UNKNOWN */
    private String actorType;

    /** 关联单据（跨模块引用，仅审计） */
    private Long documentId;

    private String moduleCode;

    /** 动作码（与权限码同规范） */
    private String actionCode;

    private String targetTable;

    private Long targetId;

    /** 请求关联 ID */
    private String requestId;

    private String clientIp;

    /** 变更前快照（一期预留） */
    private String beforeValue;

    /** 变更后快照（一期预留） */
    private String afterValue;

    private Integer success;

    private Instant occurredAt;

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getModuleCode() {
        return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
