package com.labmanage.module.booking.flow.enums;

/**
 * 审批阶段任务状态（approval_task.status）。
 * <p>
 * 场地预约本期单阶段（ADMIN_REVIEW）：READY 待管理员核对 →
 * APPROVED 已批准 / REJECTED 整单驳回 / CANCELLED 撤销或超时取消。
 * WAITING 为多阶段链路预留。
 */
public enum ApprovalTaskStatus {

    WAITING,
    READY,
    APPROVED,
    REJECTED,
    CANCELLED
}
