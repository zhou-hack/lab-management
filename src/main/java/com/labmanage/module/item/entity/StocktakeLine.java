package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 盘点行（盘点+SKU 唯一；建单时快照账面量与余额版本）
 */
@TableName("stocktake_line")
public class StocktakeLine extends BaseEntity {

    /** 所属盘点单 ID */
    private Long stocktakeId;

    /** 物料 SKU ID */
    private Long skuId;

    /** 账面量快照（建单时从 stock_balance 复制） */
    private BigDecimal bookQtySnapshot;

    /** 实盘量（非负，未录入为 NULL） */
    private BigDecimal countedQty;

    /** 余额版本快照（提交时校验一致） */
    private Integer balanceVersionSnapshot;

    /** 差异原因（差异非零必填） */
    private String differenceReason;

    /** 盘点人 ID（登录接入后写入） */
    private Long countedBy;

    /** 实盘时间（UTC） */
    private Instant countedAt;

    public Long getStocktakeId() {
        return stocktakeId;
    }

    public void setStocktakeId(Long stocktakeId) {
        this.stocktakeId = stocktakeId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public BigDecimal getBookQtySnapshot() {
        return bookQtySnapshot;
    }

    public void setBookQtySnapshot(BigDecimal bookQtySnapshot) {
        this.bookQtySnapshot = bookQtySnapshot;
    }

    public BigDecimal getCountedQty() {
        return countedQty;
    }

    public void setCountedQty(BigDecimal countedQty) {
        this.countedQty = countedQty;
    }

    public Integer getBalanceVersionSnapshot() {
        return balanceVersionSnapshot;
    }

    public void setBalanceVersionSnapshot(Integer balanceVersionSnapshot) {
        this.balanceVersionSnapshot = balanceVersionSnapshot;
    }

    public String getDifferenceReason() {
        return differenceReason;
    }

    public void setDifferenceReason(String differenceReason) {
        this.differenceReason = differenceReason;
    }

    public Long getCountedBy() {
        return countedBy;
    }

    public void setCountedBy(Long countedBy) {
        this.countedBy = countedBy;
    }

    public Instant getCountedAt() {
        return countedAt;
    }

    public void setCountedAt(Instant countedAt) {
        this.countedAt = countedAt;
    }
}
