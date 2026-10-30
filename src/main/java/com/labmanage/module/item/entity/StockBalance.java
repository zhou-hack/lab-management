package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

/**
 * 库存余额（可重建投影；库位+SKU 唯一）
 */
@TableName("stock_balance")
public class StockBalance extends BaseEntity {

    /** 库位 ID */
    private Long locationId;

    /** 物料 SKU ID */
    private Long skuId;

    /** 当前库存量（非负，可用量） */
    private BigDecimal stockQty;

    /** 安全库存（最低库存，即预警阈值） */
    private BigDecimal safeQty;

    /** 补货目标库存（≥ 安全库存） */
    private BigDecimal targetQty;

    /** 乐观锁版本 */
    private Integer version;

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

    public BigDecimal getStockQty() {
        return stockQty;
    }

    public void setStockQty(BigDecimal stockQty) {
        this.stockQty = stockQty;
    }

    public BigDecimal getSafeQty() {
        return safeQty;
    }

    public void setSafeQty(BigDecimal safeQty) {
        this.safeQty = safeQty;
    }

    public BigDecimal getTargetQty() {
        return targetQty;
    }

    public void setTargetQty(BigDecimal targetQty) {
        this.targetQty = targetQty;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
