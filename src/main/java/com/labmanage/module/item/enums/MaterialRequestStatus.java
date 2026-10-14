package com.labmanage.module.item.enums;

/**
 * 物料申领单状态（设计文档差异 3：定稿未枚举，采用建议值）
 */
public enum MaterialRequestStatus {
    /** 草稿 */
    DRAFT,
    /** 待审批（审批流接入后启用，本阶段保留枚举） */
    PENDING,
    /** 已批准（不扣库存） */
    APPROVED,
    /** 部分实发 */
    PARTIALLY_FULFILLED,
    /** 全部实发 */
    FULFILLED,
    /** 已拒绝 */
    REJECTED,
    /** 已取消 */
    CANCELLED
}
