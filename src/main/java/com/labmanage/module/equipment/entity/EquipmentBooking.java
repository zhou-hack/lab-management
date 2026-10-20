package com.labmanage.module.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.equipment.enums.BookingStatus;
import com.labmanage.module.equipment.enums.ValueLevel;

import java.time.Instant;

/**
 * 设备使用申请单实体（document_id 唯一，一单一台设备；七态流转）
 */
@TableName("equipment_booking")
public class EquipmentBooking extends BaseEntity {

    /** 统一单据（→ biz_document.id，唯一） */
    private Long documentId;

    /** 借用设备（→ equipment_asset.id） */
    private Long assetId;

    /** 申请人（→ sys_user.id） */
    private Long applicantId;

    /** 指定审批教师（→ sys_user.id；学生单必填，教师单为 NULL） */
    private Long approvalTeacherId;

    /** 借用用途 */
    private String purpose;

    /** 借用开始时间（UTC） */
    private Instant startAt;

    /** 借用结束时间（UTC，须晚于开始时间） */
    private Instant endAt;

    /** 提交时设备价值分级快照（审批链按快照执行） */
    private ValueLevel valueLevelSnapshot;

    /** 借用状态 */
    private BookingStatus status;

    /** 撤销原因 */
    private String cancelReason;

    /** 撤销时间（UTC） */
    private Instant cancelledAt;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public Long getApprovalTeacherId() {
        return approvalTeacherId;
    }

    public void setApprovalTeacherId(Long approvalTeacherId) {
        this.approvalTeacherId = approvalTeacherId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public ValueLevel getValueLevelSnapshot() {
        return valueLevelSnapshot;
    }

    public void setValueLevelSnapshot(ValueLevel valueLevelSnapshot) {
        this.valueLevelSnapshot = valueLevelSnapshot;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
