package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.ClaimStatus;
import com.labmanage.module.item.enums.PickupMode;

import java.time.Instant;
import java.time.LocalDate;

@TableName("material_claim")
public class MaterialClaim extends BaseEntity {

    private Long bizDocumentId;
    private Long applicantId;
    private String purpose;
    private LocalDate bookingDate;
    private Long startPeriodId;
    private Long endPeriodId;
    private Instant bookingStartAt;
    private Instant bookingEndAt;
    private Instant returnDeadline;
    private ClaimStatus status;
    private PickupMode pickupMode;
    private Long delegateStudentId;
    private Long delegateGrantId;
    private Instant delegatedAt;
    private Integer revisionNo;
    private Long approvedBy;
    private Instant approvedAt;
    private String rejectReason;
    private Long abandonRequestedBy;
    private Instant abandonRequestedAt;
    private String abandonReason;
    private String closeReason;
    private Long closedBy;
    private Instant closedAt;
    private Long cancelledBy;
    private Instant cancelledAt;

    public Long getBizDocumentId() { return bizDocumentId; }
    public void setBizDocumentId(Long bizDocumentId) { this.bizDocumentId = bizDocumentId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }
    public Long getStartPeriodId() { return startPeriodId; }
    public void setStartPeriodId(Long startPeriodId) { this.startPeriodId = startPeriodId; }
    public Long getEndPeriodId() { return endPeriodId; }
    public void setEndPeriodId(Long endPeriodId) { this.endPeriodId = endPeriodId; }
    public Instant getBookingStartAt() { return bookingStartAt; }
    public void setBookingStartAt(Instant bookingStartAt) { this.bookingStartAt = bookingStartAt; }
    public Instant getBookingEndAt() { return bookingEndAt; }
    public void setBookingEndAt(Instant bookingEndAt) { this.bookingEndAt = bookingEndAt; }
    public Instant getReturnDeadline() { return returnDeadline; }
    public void setReturnDeadline(Instant returnDeadline) { this.returnDeadline = returnDeadline; }
    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }
    public PickupMode getPickupMode() { return pickupMode; }
    public void setPickupMode(PickupMode pickupMode) { this.pickupMode = pickupMode; }
    public Long getDelegateStudentId() { return delegateStudentId; }
    public void setDelegateStudentId(Long delegateStudentId) { this.delegateStudentId = delegateStudentId; }
    public Long getDelegateGrantId() { return delegateGrantId; }
    public void setDelegateGrantId(Long delegateGrantId) { this.delegateGrantId = delegateGrantId; }
    public Instant getDelegatedAt() { return delegatedAt; }
    public void setDelegatedAt(Instant delegatedAt) { this.delegatedAt = delegatedAt; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Long getAbandonRequestedBy() { return abandonRequestedBy; }
    public void setAbandonRequestedBy(Long abandonRequestedBy) { this.abandonRequestedBy = abandonRequestedBy; }
    public Instant getAbandonRequestedAt() { return abandonRequestedAt; }
    public void setAbandonRequestedAt(Instant abandonRequestedAt) { this.abandonRequestedAt = abandonRequestedAt; }
    public String getAbandonReason() { return abandonReason; }
    public void setAbandonReason(String abandonReason) { this.abandonReason = abandonReason; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Long getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
