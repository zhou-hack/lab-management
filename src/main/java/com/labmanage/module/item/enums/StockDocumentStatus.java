package com.labmanage.module.item.enums;

/**
 * 库存单据状态
 */
public enum StockDocumentStatus {
    /** 草稿（可编辑、可取消） */
    DRAFT,
    /** 已提交审批（审批中心接入后使用） */
    PENDING,
    /** 已批准（可过账） */
    APPROVED,
    /** 已过账（不可编辑，产生流水与余额变动） */
    POSTED,
    /** 已取消 */
    CANCELLED
}
