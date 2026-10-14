package com.labmanage.module.document.enums;

/**
 * 审批任务状态
 */
public enum TaskStatus {

    /** 等待前置阶段完成 */
    WAITING,

    /** 待审批（当前生效） */
    READY,

    /** 已通过 */
    APPROVED,

    /** 已驳回 */
    REJECTED,

    /** 已取消（链式取消：前置驳回/单据撤销） */
    CANCELLED
}
