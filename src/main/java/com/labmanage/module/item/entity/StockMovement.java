package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("stock_movement")
public class StockMovement extends BaseEntity {

    private String movementKey;
    private String movementType;
    private String direction;
    private Long materialId;
    private Long fixedAssetId;
    private Long locationId;
    private BigDecimal quantity;
    private Long materialDocumentId;
    private Long materialDocumentLineId;
    private Long operatorId;
    private Instant occurredAt;
    private BigDecimal beforeOwnedQuantity;
    private BigDecimal afterOwnedQuantity;
    private BigDecimal beforeAvailableQuantity;
    private BigDecimal afterAvailableQuantity;

    public String getMovementKey() { return movementKey; }
    public void setMovementKey(String movementKey) { this.movementKey = movementKey; }
    public String getMovementType() { return movementType; }
    public void setMovementType(String movementType) { this.movementType = movementType; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getFixedAssetId() { return fixedAssetId; }
    public void setFixedAssetId(Long fixedAssetId) { this.fixedAssetId = fixedAssetId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public Long getMaterialDocumentId() { return materialDocumentId; }
    public void setMaterialDocumentId(Long materialDocumentId) { this.materialDocumentId = materialDocumentId; }
    public Long getMaterialDocumentLineId() { return materialDocumentLineId; }
    public void setMaterialDocumentLineId(Long materialDocumentLineId) { this.materialDocumentLineId = materialDocumentLineId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public BigDecimal getBeforeOwnedQuantity() { return beforeOwnedQuantity; }
    public void setBeforeOwnedQuantity(BigDecimal beforeOwnedQuantity) { this.beforeOwnedQuantity = beforeOwnedQuantity; }
    public BigDecimal getAfterOwnedQuantity() { return afterOwnedQuantity; }
    public void setAfterOwnedQuantity(BigDecimal afterOwnedQuantity) { this.afterOwnedQuantity = afterOwnedQuantity; }
    public BigDecimal getBeforeAvailableQuantity() { return beforeAvailableQuantity; }
    public void setBeforeAvailableQuantity(BigDecimal beforeAvailableQuantity) { this.beforeAvailableQuantity = beforeAvailableQuantity; }
    public BigDecimal getAfterAvailableQuantity() { return afterAvailableQuantity; }
    public void setAfterAvailableQuantity(BigDecimal afterAvailableQuantity) { this.afterAvailableQuantity = afterAvailableQuantity; }
}
