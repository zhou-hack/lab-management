package com.labmanage.module.item.enums;

/**
 * 物料申领单状态（设计文档差异 3：定稿未枚举，采用建议值；
 * 2026-10-19 两级审核：SELF 经 DRAFT→TEACHER_APPROVED（老师审核）→APPROVED（管理员终审），
 * PROXY 学生代理代领创建即 TEACHER_APPROVED，直接交由管理员终审）
 */
public enum MaterialRequestStatus {
    /** 草稿 */
    DRAFT,
    /** 待审批（审批流接入后启用，本阶段保留枚举） */
    PENDING,
    /** 老师已审核（待管理员终审；学生代理代领单创建即此状态） */
    TEACHER_APPROVED,
    /** 已批准（管理员终审通过，不扣库存） */
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
