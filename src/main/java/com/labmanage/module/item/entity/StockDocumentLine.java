package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

/**
 * 库存单据行（quantity > 0；出入方向由 movement_type 校验）
 */
@TableName("stock_document_line")
public class StockDocumentLine extends BaseEntity {

    /** 所属单据 ID */
    private Long stockDocumentId;

    /** 行号（单据内 1 起连续） */
    private Integer lineNo;

    /** 物料 SKU ID */
    private Long skuId;

    /** 出库位 ID（ISSUE/调拨必填） */
    private Long fromLocationId;

    /** 入库位 ID（OPENING/PURCHASE_IN/RETURN_IN 必填） */
    private Long toLocationId;

    /** 数量（> 0，精度不超过 SKU.quantity_scale） */
    private BigDecimal quantity;

    /** 单价快照（入库登记价格 / 过账时成本） */
    private BigDecimal unitPriceSnapshot;

    /** 金额快照（数量 × 单价，两位小数） */
    private BigDecimal amountSnapshot;

    /** 绑定申领行 ID（直发领用为 NULL） */
    private Long requestLineId;

    /** 归还绑定的原出库行 ID */
    private Long originalIssueLineId;

    /** 盘点调账方向 */
    private String adjustDirection;

    public Long getStockDocumentId() {
        return stockDocumentId;
    }

    public void setStockDocumentId(Long stockDocumentId) {
        this.stockDocumentId = stockDocumentId;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public Long getFromLocationId() {
        return fromLocationId;
    }

    public void setFromLocationId(Long fromLocationId) {
        this.fromLocationId = fromLocationId;
    }

    public Long getToLocationId() {
        return toLocationId;
    }

    public void setToLocationId(Long toLocationId) {
        this.toLocationId = toLocationId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public BigDecimal getAmountSnapshot() {
        return amountSnapshot;
    }

    public void setAmountSnapshot(BigDecimal amountSnapshot) {
        this.amountSnapshot = amountSnapshot;
    }

    public Long getRequestLineId() {
        return requestLineId;
    }

    public void setRequestLineId(Long requestLineId) {
        this.requestLineId = requestLineId;
    }

    public Long getOriginalIssueLineId() {
        return originalIssueLineId;
    }

    public void setOriginalIssueLineId(Long originalIssueLineId) {
        this.originalIssueLineId = originalIssueLineId;
    }

    public String getAdjustDirection() {
        return adjustDirection;
    }

    public void setAdjustDirection(String adjustDirection) {
        this.adjustDirection = adjustDirection;
    }
}
