package com.labmanage.module.equipment.enums;

/**
 * 器材借用单状态（业务表为当前业务状态的唯一来源）
 * <p>
 * 学生单：PENDING_TEACHER → PENDING_ADMIN → APPROVED → CHECKED_OUT → RETURNED；
 * 老师单：PENDING_ADMIN → APPROVED → CHECKED_OUT → RETURNED；
 * 本期普通/贵重设备审批链一致（按用户确认执行）。
 */
public enum BookingStatus {

    /** 待指定老师审批（学生单第一级） */
    PENDING_TEACHER,

    /** 待管理员审批发放（末级） */
    PENDING_ADMIN,

    /** 已批准，待借出 */
    APPROVED,

    /** 已借出 */
    CHECKED_OUT,

    /** 已归还 */
    RETURNED,

    /** 已驳回 */
    REJECTED,

    /** 已撤销（仅 PENDING 态申请人本人可撤销） */
    CANCELLED
}
