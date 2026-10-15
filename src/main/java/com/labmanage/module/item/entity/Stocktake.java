package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.StocktakeStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 盘点单（COUNTING/PENDING_REVIEW 期间库位冻结；同一库位最多一张活跃单）
 */
@TableName("stocktake")
public class Stocktake extends BaseEntity {

    /** 统一单据 ID（单据中心建成前由本模块预生成占位） */
    private Long documentId;

    /** 盘点库位 ID */
    private Long locationId;

    /** 学期 ID（跨模块引用） */
    private Long semesterId;

    /** 盘点日期（纯日期，不做时区转换） */
    private LocalDate countDate;

    /** 状态 */
    private StocktakeStatus status;

    /** 开始时间（UTC，COUNTING 起有值） */
    private Instant startedAt;

    /** 完成时间（UTC，COMPLETED 有值） */
    private Instant finishedAt;

    /** 生成的调账单 ID（差异非零时生成） */
    private Long adjustDocumentId;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(Long semesterId) {
        this.semesterId = semesterId;
    }

    public LocalDate getCountDate() {
        return countDate;
    }

    public void setCountDate(LocalDate countDate) {
        this.countDate = countDate;
    }

    public StocktakeStatus getStatus() {
        return status;
    }

    public void setStatus(StocktakeStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getAdjustDocumentId() {
        return adjustDocumentId;
    }

    public void setAdjustDocumentId(Long adjustDocumentId) {
        this.adjustDocumentId = adjustDocumentId;
    }

    /** 便捷判断：是否处于库位冻结的活跃状态 */
    public boolean freezing() {
        return status == StocktakeStatus.COUNTING || status == StocktakeStatus.PENDING_REVIEW;
    }
}
