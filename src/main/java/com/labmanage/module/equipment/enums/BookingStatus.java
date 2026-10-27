package com.labmanage.module.equipment.enums;

/**
 * 设备使用申请单状态（状态机见 docs/固定设备三权级流程清单.md 第 5.1 节）
 */
public enum BookingStatus {

    /** 学生单待教师初审 */
    PENDING_TEACHER,

    /** 待管理员终审（学生单两级链第二级 / 教师单唯一审批环节） */
    PENDING_ADMIN,

    /** 审批通过（可登记借出发放） */
    APPROVED,

    /** 已借出（可登记归还） */
    CHECKED_OUT,

    /** 到期未归还（逾期补登记归还；进入时冻结申请人 7 天申请权限） */
    OVERDUE,

    /** 已归还（终态） */
    RETURNED,

    /** 已驳回（终态；必填原因） */
    REJECTED,

    /** 已撤销（终态；借出前本人可撤销） */
    CANCELLED
}
