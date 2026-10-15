package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;

import java.time.Instant;

/**
 * 库存单据（movement_type 区分业务动作）
 */
@TableName("stock_document")
public class StockDocument extends BaseEntity {

    /** 统一单据 ID（单据中心建成前由本模块预生成雪花 ID 占位） */
    private Long documentId;

    /** 业务类型 */
    private MovementType movementType;

    /** 单据状态 */
    private StockDocumentStatus status;

    /** 实际经办人 ID（登录模块接入后由会话写入） */
    private Long operatorId;

    /** 归属老师 ID（助理代办时记录） */
    private Long ownerTeacherId;

    /** 使用的助理授权 ID */
    private Long grantId;

    /** 关联申领单 ID（直发领用为 NULL） */
    private Long materialRequestId;

    /** 关联场地预约 ID */
    private Long labBookingId;

    /** 业务发生时间（UTC） */
    private Instant occurredAt;

    /** 过账时间（UTC，仅 POSTED 有值） */
    private Instant postedAt;

    /** 冲销原单 ID */
    private Long reversalOfId;

    /** 事由 */
    private String reason;

    /** 供应商名称快照 */
    private String vendorName;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public StockDocumentStatus getStatus() {
        return status;
    }

    public void setStatus(StockDocumentStatus status) {
        this.status = status;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getOwnerTeacherId() {
        return ownerTeacherId;
    }

    public void setOwnerTeacherId(Long ownerTeacherId) {
        this.ownerTeacherId = ownerTeacherId;
    }

    public Long getGrantId() {
        return grantId;
    }

    public void setGrantId(Long grantId) {
        this.grantId = grantId;
    }

    public Long getMaterialRequestId() {
        return materialRequestId;
    }

    public void setMaterialRequestId(Long materialRequestId) {
        this.materialRequestId = materialRequestId;
    }

    public Long getLabBookingId() {
        return labBookingId;
    }

    public void setLabBookingId(Long labBookingId) {
        this.labBookingId = labBookingId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Long getReversalOfId() {
        return reversalOfId;
    }

    public void setReversalOfId(Long reversalOfId) {
        this.reversalOfId = reversalOfId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }
}
