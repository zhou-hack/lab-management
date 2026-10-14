package com.labmanage.module.item.enums;

/**
 * 盘点单状态
 */
public enum StocktakeStatus {
    /** 草稿（未冻结库位） */
    DRAFT,
    /** 盘点中（库位冻结，禁止库存写入） */
    COUNTING,
    /** 待复核（库位冻结，等待调账审批） */
    PENDING_REVIEW,
    /** 已完成（调账过账后解冻） */
    COMPLETED,
    /** 已取消（未开始盘点前可取消） */
    CANCELLED
}
