package com.labmanage.module.document.enums;

/**
 * 通用审批阶段任务状态（approval_task.status）。
 * <p>
 * WAITING 等待前序阶段，READY 待处理；APPROVED 已批准、REJECTED 已驳回、
 * CANCELLED 已取消。
 */
public enum ApprovalTaskStatus {

    WAITING,
    READY,
    APPROVED,
    REJECTED,
    CANCELLED
}
