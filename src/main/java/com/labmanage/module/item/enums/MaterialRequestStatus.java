package com.labmanage.module.item.enums;

/**
 * 物料申领单状态（设计文档差异 3：定稿未枚举，采用建议值；
 * 2026-10-22 学生预约下线后不再有 DRAFT/PENDING：PROXY/TEACHER 均创建即 TEACHER_APPROVED，
 * 直接交由管理员终审；2026-10-22 起「部分实发」派生态砍除——实发进度由申领行
 * fulfilled_qty/approved_qty 表达，单据仅在全部行发完时推进 FULFILLED）
 */
public enum MaterialRequestStatus {
    /** 老师已审核（待管理员终审；PROXY/TEACHER 单创建即此状态） */
    TEACHER_APPROVED,
    /** 已批准（管理员终审通过，不扣库存；部分实发时保持此状态） */
    APPROVED,
    /** 全部实发（全部行实发量达批准量，终态） */
    FULFILLED,
    /** 已拒绝 */
    REJECTED,
    /** 已取消 */
    CANCELLED
}
