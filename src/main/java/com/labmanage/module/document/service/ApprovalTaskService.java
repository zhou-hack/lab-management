package com.labmanage.module.document.service;

import com.labmanage.module.document.entity.ApprovalTask;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.enums.ApprovalStage;
import com.labmanage.module.document.enums.TaskStatus;

/**
 * 审批阶段待办服务（内部域：待办生成与状态流转，均由调用方保证事务边界）
 */
public interface ApprovalTaskService {

    /**
     * 生成审批任务（轮次固定 1；available_at/timeout_action 由实现统一填充）
     */
    ApprovalTask createTask(BizDocument document, ApprovalStage stage, int sequenceNo,
                            String requiredRole, Long assignedUserId, TaskStatus status);

    /**
     * 按主键查询任务，不存在抛业务异常
     */
    ApprovalTask requireTask(Long taskId);

    /**
     * 审批通过：READY → APPROVED（条件更新防并发重复审批）
     */
    void approve(Long taskId, Long decidedBy, String comment);

    /**
     * 审批驳回：READY → REJECTED（意见由调用方校验必填）
     */
    void reject(Long taskId, Long decidedBy, String comment);

    /**
     * 链式取消：WAITING/READY → CANCELLED（前置驳回或单据撤销时）
     */
    void cancel(Long taskId);

    /**
     * 激活下一阶段：WAITING → READY
     */
    void activate(Long taskId);
}
