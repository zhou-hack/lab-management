package com.labmanage.module.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.document.enums.ApprovalStage;
import com.labmanage.module.document.enums.TaskStatus;
import com.labmanage.module.document.enums.TimeoutAction;

import java.time.Instant;

/**
 * 审批阶段待办实体（阶段流转；追加型历史数据，deleted 恒 0）
 */
@TableName("approval_task")
public class ApprovalTask extends BaseEntity {

    /** 所属单据（→ biz_document.id） */
    private Long documentId;

    /** 审批阶段 */
    private ApprovalStage stageCode;

    /** 阶段顺序号（从 1 起） */
    private Integer sequenceNo;

    /** 审批轮次（默认 1） */
    private Integer roundNo;

    /** 要求角色码（→ sys_role.role_code） */
    private String requiredRole;

    /** 指定审批人（→ sys_user.id；NULL=按角色+范围匹配） */
    private Long assignedUserId;

    /** 指定审批部门（→ sys_department.id） */
    private Long assignedDepartmentId;

    /** 任务状态 */
    private TaskStatus status;

    /** 任务生效时间（UTC） */
    private Instant availableAt;

    /** 催办时间（UTC） */
    private Instant remindAt;

    /** 升级时间（UTC） */
    private Instant escalateAt;

    /** 超时时间（UTC） */
    private Instant timeoutAt;

    /** 超时动作（本期固定 NONE） */
    private TimeoutAction timeoutAction;

    /** 实际审批人（代批时与 assigned 不同） */
    private Long decidedBy;

    /** 审批时间（UTC） */
    private Instant decidedAt;

    /** 审批意见（驳回必填） */
    private String decisionComment;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public ApprovalStage getStageCode() {
        return stageCode;
    }

    public void setStageCode(ApprovalStage stageCode) {
        this.stageCode = stageCode;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(String requiredRole) {
        this.requiredRole = requiredRole;
    }

    public Long getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(Long assignedUserId) {
        this.assignedUserId = assignedUserId;
    }

    public Long getAssignedDepartmentId() {
        return assignedDepartmentId;
    }

    public void setAssignedDepartmentId(Long assignedDepartmentId) {
        this.assignedDepartmentId = assignedDepartmentId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public void setAvailableAt(Instant availableAt) {
        this.availableAt = availableAt;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(Instant remindAt) {
        this.remindAt = remindAt;
    }

    public Instant getEscalateAt() {
        return escalateAt;
    }

    public void setEscalateAt(Instant escalateAt) {
        this.escalateAt = escalateAt;
    }

    public Instant getTimeoutAt() {
        return timeoutAt;
    }

    public void setTimeoutAt(Instant timeoutAt) {
        this.timeoutAt = timeoutAt;
    }

    public TimeoutAction getTimeoutAction() {
        return timeoutAction;
    }

    public void setTimeoutAction(TimeoutAction timeoutAction) {
        this.timeoutAction = timeoutAction;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(Long decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public void setDecisionComment(String decisionComment) {
        this.decisionComment = decisionComment;
    }
}
