package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

@TableName("material_document")
public class MaterialDocument extends BaseEntity {

    private Long bizDocumentId;
    private String documentNo;
    private Long businessChainId;
    private String documentType;
    private String businessScene;
    private Integer documentSeq;
    private Long sourceMaterialDocumentId;
    private Long locationId;
    private Long fromLocationId;
    private Long toLocationId;
    private String transferStatus;
    private Long receivedBy;
    private Instant receivedAt;
    private String reviewStatus;
    private Long reviewedBy;
    private Instant reviewedAt;
    private String reviewReason;
    private Long operatorId;
    private Long responsibleTeacherId;
    private Long pickupUserId;
    private Instant occurredAt;
    private Instant postedAt;
    private String processStatus;
    private String returnStatus;
    private String overdueReason;
    private String idempotencyKey;
    private Integer version;
    private String remark;

    public Long getBizDocumentId() { return bizDocumentId; }
    public void setBizDocumentId(Long bizDocumentId) { this.bizDocumentId = bizDocumentId; }
    public String getDocumentNo() { return documentNo; }
    public void setDocumentNo(String documentNo) { this.documentNo = documentNo; }
    public Long getBusinessChainId() { return businessChainId; }
    public void setBusinessChainId(Long businessChainId) { this.businessChainId = businessChainId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getBusinessScene() { return businessScene; }
    public void setBusinessScene(String businessScene) { this.businessScene = businessScene; }
    public Integer getDocumentSeq() { return documentSeq; }
    public void setDocumentSeq(Integer documentSeq) { this.documentSeq = documentSeq; }
    public Long getSourceMaterialDocumentId() { return sourceMaterialDocumentId; }
    public void setSourceMaterialDocumentId(Long sourceMaterialDocumentId) { this.sourceMaterialDocumentId = sourceMaterialDocumentId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(Long fromLocationId) { this.fromLocationId = fromLocationId; }
    public Long getToLocationId() { return toLocationId; }
    public void setToLocationId(Long toLocationId) { this.toLocationId = toLocationId; }
    public String getTransferStatus() { return transferStatus; }
    public void setTransferStatus(String transferStatus) { this.transferStatus = transferStatus; }
    public Long getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Long receivedBy) { this.receivedBy = receivedBy; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public Long getResponsibleTeacherId() { return responsibleTeacherId; }
    public void setResponsibleTeacherId(Long responsibleTeacherId) { this.responsibleTeacherId = responsibleTeacherId; }
    public Long getPickupUserId() { return pickupUserId; }
    public void setPickupUserId(Long pickupUserId) { this.pickupUserId = pickupUserId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public String getProcessStatus() { return processStatus; }
    public void setProcessStatus(String processStatus) { this.processStatus = processStatus; }
    public String getReturnStatus() { return returnStatus; }
    public void setReturnStatus(String returnStatus) { this.returnStatus = returnStatus; }
    public String getOverdueReason() { return overdueReason; }
    public void setOverdueReason(String overdueReason) { this.overdueReason = overdueReason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
