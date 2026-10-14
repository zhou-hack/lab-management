package com.labmanage.module.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.document.entity.ApprovalTask;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.enums.ApprovalStage;
import com.labmanage.module.document.enums.TaskStatus;
import com.labmanage.module.document.enums.TimeoutAction;
import com.labmanage.module.document.mapper.ApprovalTaskMapper;
import com.labmanage.module.document.service.ApprovalTaskService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 审批阶段待办服务实现
 */
@Service
public class ApprovalTaskServiceImpl implements ApprovalTaskService {

    private final ApprovalTaskMapper taskMapper;

    public ApprovalTaskServiceImpl(ApprovalTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public ApprovalTask createTask(BizDocument document, ApprovalStage stage, int sequenceNo,
                                   String requiredRole, Long assignedUserId, TaskStatus status) {
        ApprovalTask task = new ApprovalTask();
        task.setDocumentId(document.getId());
        task.setStageCode(stage);
        task.setSequenceNo(sequenceNo);
        task.setRoundNo(1);
        task.setRequiredRole(requiredRole);
        task.setAssignedUserId(assignedUserId);
        task.setStatus(status);
        task.setAvailableAt(Instant.now());
        task.setTimeoutAction(TimeoutAction.NONE);
        taskMapper.insert(task);
        return task;
    }

    @Override
    public ApprovalTask requireTask(Long taskId) {
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "审批任务不存在");
        }
        return task;
    }

    @Override
    public void approve(Long taskId, Long decidedBy, String comment) {
        decide(taskId, TaskStatus.APPROVED, decidedBy, comment);
    }

    @Override
    public void reject(Long taskId, Long decidedBy, String comment) {
        decide(taskId, TaskStatus.REJECTED, decidedBy, comment);
    }

    @Override
    public void cancel(Long taskId) {
        ApprovalTask update = new ApprovalTask();
        update.setStatus(TaskStatus.CANCELLED);
        int rows = taskMapper.update(update, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getId, taskId)
                .in(ApprovalTask::getStatus, TaskStatus.WAITING, TaskStatus.READY));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "审批任务已结束，无法取消");
        }
    }

    @Override
    public void activate(Long taskId) {
        ApprovalTask update = new ApprovalTask();
        update.setStatus(TaskStatus.READY);
        update.setAvailableAt(Instant.now());
        int rows = taskMapper.update(update, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getId, taskId)
                .eq(ApprovalTask::getStatus, TaskStatus.WAITING));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "审批任务不在等待状态，无法激活");
        }
    }

    /**
     * 审批决定：READY 条件更新，防并发重复审批
     */
    private void decide(Long taskId, TaskStatus target, Long decidedBy, String comment) {
        ApprovalTask update = new ApprovalTask();
        update.setStatus(target);
        update.setDecidedBy(decidedBy);
        update.setDecidedAt(Instant.now());
        if (StringUtils.hasText(comment)) {
            update.setDecisionComment(comment);
        }
        int rows = taskMapper.update(update, new LambdaUpdateWrapper<ApprovalTask>()
                .eq(ApprovalTask::getId, taskId)
                .eq(ApprovalTask::getStatus, TaskStatus.READY));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "审批任务不在待审批状态");
        }
    }
}
