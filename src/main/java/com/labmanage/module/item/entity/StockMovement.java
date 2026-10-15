package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.MoveDirection;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 库存流水（不可变，只追加；余额可由流水重建）
 */
@TableName("stock_movement")
public class StockMovement extends BaseEntity {

    /** 来源单据行 ID */
    private Long documentLineId;

    /** 库位 ID */
    private Long locationId;

    /** 物料 SKU ID */
    private Long skuId;

    /** 方向：IN / OUT */
    private MoveDirection direction;

    /** 净变动（IN 为正，OUT 为负） */
    private BigDecimal qtyDelta;

    /** 变更前余额 */
    private BigDecimal beforeQty;

    /** 变更后余额 */
    private BigDecimal afterQty;

    /** 价值变动 */
    private BigDecimal valueDelta;

    /** 过账前移动加权均价 */
    private BigDecimal beforeAvgPrice;

    /** 过账后移动加权均价 */
    private BigDecimal afterAvgPrice;

    /** 发生时间（UTC，取单据 occurred_at） */
    private Instant occurredAt;

    /** 幂等键（STK:{documentId}:{lineNo}:{方向}，唯一） */
    private String movementKey;

    public Long getDocumentLineId() {
        return documentLineId;
    }

    public void setDocumentLineId(Long documentLineId) {
        this.documentLineId = documentLineId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public MoveDirection getDirection() {
        return direction;
    }

    public void setDirection(MoveDirection direction) {
        this.direction = direction;
    }

    public BigDecimal getQtyDelta() {
        return qtyDelta;
    }

    public void setQtyDelta(BigDecimal qtyDelta) {
        this.qtyDelta = qtyDelta;
    }

    public BigDecimal getBeforeQty() {
        return beforeQty;
    }

    public void setBeforeQty(BigDecimal beforeQty) {
        this.beforeQty = beforeQty;
    }

    public BigDecimal getAfterQty() {
        return afterQty;
    }

    public void setAfterQty(BigDecimal afterQty) {
        this.afterQty = afterQty;
    }

    public BigDecimal getValueDelta() {
        return valueDelta;
    }

    public void setValueDelta(BigDecimal valueDelta) {
        this.valueDelta = valueDelta;
    }

    public BigDecimal getBeforeAvgPrice() {
        return beforeAvgPrice;
    }

    public void setBeforeAvgPrice(BigDecimal beforeAvgPrice) {
        this.beforeAvgPrice = beforeAvgPrice;
    }

    public BigDecimal getAfterAvgPrice() {
        return afterAvgPrice;
    }

    public void setAfterAvgPrice(BigDecimal afterAvgPrice) {
        this.afterAvgPrice = afterAvgPrice;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getMovementKey() {
        return movementKey;
    }

    public void setMovementKey(String movementKey) {
        this.movementKey = movementKey;
    }
}
