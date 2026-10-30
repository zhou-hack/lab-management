package com.labmanage.module.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.document.enums.ApprovalTaskStatus;

import java.time.Instant;

/**
 * 通用审批阶段任务实体（approval_task）。
 * <p>
 * sequence_no 表示阶段顺序，round_no 表示审批轮次；任务由 READY 进入
 * APPROVED / REJECTED / CANCELLED，WAITING 用于等待前序阶段。
 */
@TableName("approval_task")
public class ApprovalTask extends BaseEntity {

    /** 所属单据（→ biz_document.id） */
    private Long documentId;

    /** 审批阶段码 */
    private String stageCode;

    /** 阶段顺序号（从 1 起） */
    private Integer sequenceNo;

    /** 审批轮次（默认 1，重新提交时递增） */
    private Integer roundNo;

    /** 要求角色码 */
    private String requiredRole;

    /** 指定审批人（NULL=按角色匹配） */
    private Long assignedUserId;

    /** 指定审批部门（可空） */
    private Long assignedDepartmentId;

    /** 任务状态 */
    private ApprovalTaskStatus status;

    /** 任务生效时间（UTC，READY 时可操作） */
    private Instant availableAt;

    /** 催办时间（UTC，可空） */
    private Instant remindAt;

    /** 升级时间（UTC，可空） */
    private Instant escalateAt;

    /** 超时时间（UTC，可空） */
    private Instant timeoutAt;

    /** 超时动作（如 CANCEL/NONE） */
    private String timeoutAction;

    /** 实际审批人（→ sys_user.id） */
    private Long decidedBy;

    /** 审批时间（UTC） */
    private Instant decidedAt;

    /** 审批意见 */
    private String decisionComment;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getStageCode() {
        return stageCode;
    }

    public void setStageCode(String stageCode) {
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

    public ApprovalTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalTaskStatus status) {
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

    public String getTimeoutAction() {
        return timeoutAction;
    }

    public void setTimeoutAction(String timeoutAction) {
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
