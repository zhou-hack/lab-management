package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("material_reservation")
public class MaterialReservation extends BaseEntity {

    private Long claimId;
    private Long claimLineId;
    private Long allocationId;
    private Integer revisionNo;
    private Long materialId;
    private Long locationId;
    private Long fixedAssetId;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long reservationSubjectId;

    private String reservationRule;
    private Integer assetTotalSnapshot;
    private BigDecimal reservedQuantity;
    private BigDecimal consumedQuantity;
    private BigDecimal releasedQuantity;
    private Instant reservationStartAt;
    private Instant reservationEndAt;
    private ReservationStatus status;
    private Instant closedAt;

    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public Long getClaimLineId() { return claimLineId; }
    public void setClaimLineId(Long claimLineId) { this.claimLineId = claimLineId; }
    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getFixedAssetId() { return fixedAssetId; }
    public void setFixedAssetId(Long fixedAssetId) { this.fixedAssetId = fixedAssetId; }
    public Long getReservationSubjectId() { return reservationSubjectId; }
    public void setReservationSubjectId(Long reservationSubjectId) { this.reservationSubjectId = reservationSubjectId; }
    public String getReservationRule() { return reservationRule; }
    public void setReservationRule(String reservationRule) { this.reservationRule = reservationRule; }
    public Integer getAssetTotalSnapshot() { return assetTotalSnapshot; }
    public void setAssetTotalSnapshot(Integer assetTotalSnapshot) { this.assetTotalSnapshot = assetTotalSnapshot; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public BigDecimal getConsumedQuantity() { return consumedQuantity; }
    public void setConsumedQuantity(BigDecimal consumedQuantity) { this.consumedQuantity = consumedQuantity; }
    public BigDecimal getReleasedQuantity() { return releasedQuantity; }
    public void setReleasedQuantity(BigDecimal releasedQuantity) { this.releasedQuantity = releasedQuantity; }
    public Instant getReservationStartAt() { return reservationStartAt; }
    public void setReservationStartAt(Instant reservationStartAt) { this.reservationStartAt = reservationStartAt; }
    public Instant getReservationEndAt() { return reservationEndAt; }
    public void setReservationEndAt(Instant reservationEndAt) { this.reservationEndAt = reservationEndAt; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}
