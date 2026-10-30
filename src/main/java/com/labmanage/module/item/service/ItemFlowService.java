package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.document.entity.ApprovalTask;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.document.enums.ApprovalTaskStatus;
import com.labmanage.module.document.mapper.ApprovalTaskMapper;
import com.labmanage.module.document.mapper.BizDocumentMapper;
import com.labmanage.module.document.mapper.DocumentEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/**
 * 物料共享流程服务，仅封装统一单据、审批任务和单据事件。
 * <p>
 * 物料业务状态仍由物料业务表维护；本服务不依赖场地流程，也不定义物料单据类型。
 */
@Service
public class ItemFlowService {

    private static final Logger log = LoggerFactory.getLogger(ItemFlowService.class);

    private final BizDocumentMapper documentMapper;
    private final ApprovalTaskMapper taskMapper;
    private final DocumentEventMapper eventMapper;
    private final Clock clock;

    public ItemFlowService(BizDocumentMapper documentMapper,
                           ApprovalTaskMapper taskMapper,
                           DocumentEventMapper eventMapper,
                           @Qualifier("itemClock") Clock clock) {
        this.documentMapper = documentMapper;
        this.taskMapper = taskMapper;
        this.eventMapper = eventMapper;
        this.clock = clock;
    }

    /** 创建统一单据；相同 documentNo 已存在时返回原单据。 */
    @Transactional(rollbackFor = Exception.class)
    public BizDocument createDocument(BizDocument document) {
        BizDocument existing = documentMapper.selectByDocumentNo(document.getDocumentNo());
        if (existing != null) {
            return existing;
        }
        if (document.getSubmittedAt() == null) {
            document.setSubmittedAt(now());
        }
        documentMapper.insert(document);
        return document;
    }

    /** 按归属人、单据类型和幂等键查询，避免跨用户读取他人单据。 */
    public BizDocument findByOwnerIdempotency(Long ownerUserId, String documentType, String idempotencyKey) {
        return documentMapper.selectOne(Wrappers.<BizDocument>lambdaQuery()
                .eq(BizDocument::getOwnerUserId, ownerUserId)
                .eq(BizDocument::getDocumentType, documentType)
                .eq(BizDocument::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    /** 创建按归属人幂等的共享单据；并发唯一键冲突时读取先提交的原单据。 */
    @Transactional(rollbackFor = Exception.class)
    public BizDocument createDocumentByOwnerIdempotency(BizDocument document) {
        BizDocument existing = findByOwnerIdempotency(document.getOwnerUserId(),
                document.getDocumentType(), document.getIdempotencyKey());
        if (existing != null) {
            return existing;
        }
        try {
            return createDocument(document);
        } catch (DuplicateKeyException e) {
            BizDocument concurrent = documentMapper.selectByOwnerIdempotencyForUpdate(
                    document.getOwnerUserId(), document.getDocumentType(), document.getIdempotencyKey());
            if (concurrent != null) {
                return concurrent;
            }
            throw e;
        }
    }

    /** 按主键查询统一单据，不存在返回 null。 */
    public BizDocument findDocumentById(Long documentId) {
        return documentMapper.selectById(documentId);
    }

    /** 批量查询共享单据，供列表避免逐条读取。 */
    public List<BizDocument> listDocumentsByIds(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return documentMapper.selectBatchIds(documentIds);
    }

    /** 按单据编号查询统一单据，不存在返回 null。 */
    public BizDocument findDocumentByNo(String documentNo) {
        return documentMapper.selectByDocumentNo(documentNo);
    }

    /**
     * 创建审批任务。roundNo 缺省为 1、status 缺省为 READY、availableAt 缺省为当前时间；
     * 重新审核时可先通过 nextRoundNo 计算轮次，再取消旧任务并创建新任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalTask createTask(ApprovalTask task) {
        if (task.getRoundNo() == null) {
            task.setRoundNo(1);
        }
        if (task.getStatus() == null) {
            task.setStatus(ApprovalTaskStatus.READY);
        }
        if (task.getAvailableAt() == null) {
            task.setAvailableAt(now());
        }
        taskMapper.insert(task);
        return task;
    }

    /** 查询指定阶段最新一轮任务，不存在返回 null。 */
    public ApprovalTask findLatestTask(Long documentId, String stageCode, Integer sequenceNo) {
        return taskMapper.selectOne(Wrappers.<ApprovalTask>lambdaQuery()
                .eq(ApprovalTask::getDocumentId, documentId)
                .eq(ApprovalTask::getStageCode, stageCode)
                .eq(ApprovalTask::getSequenceNo, sequenceNo)
                .orderByDesc(ApprovalTask::getRoundNo)
                .last("LIMIT 1"));
    }

    /** 查询单据全部审批任务，按轮次、阶段顺序和主键升序。 */
    public List<ApprovalTask> listTasks(Long documentId) {
        return taskMapper.selectList(Wrappers.<ApprovalTask>lambdaQuery()
                .eq(ApprovalTask::getDocumentId, documentId)
                .orderByAsc(ApprovalTask::getRoundNo)
                .orderByAsc(ApprovalTask::getSequenceNo)
                .orderByAsc(ApprovalTask::getId));
    }

    /** 计算指定阶段下一轮审批的 roundNo；没有历史任务时返回 1。 */
    public int nextRoundNo(Long documentId, String stageCode, Integer sequenceNo) {
        ApprovalTask latest = findLatestTask(documentId, stageCode, sequenceNo);
        return latest == null ? 1 : latest.getRoundNo() + 1;
    }

    /** 将 READY 任务完成为 APPROVED 或 REJECTED，并记录审批人和意见。 */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalTask completeTask(Long taskId, ApprovalTaskStatus targetStatus,
                                     Long decidedBy, String decisionComment) {
        if (targetStatus != ApprovalTaskStatus.APPROVED && targetStatus != ApprovalTaskStatus.REJECTED) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "审批任务只能完成为批准或驳回");
        }
        ApprovalTask current = taskMapper.selectById(taskId);
        if (current == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "审批任务不存在");
        }
        Instant now = now();
        int rows = taskMapper.update(null, Wrappers.<ApprovalTask>lambdaUpdate()
                .eq(ApprovalTask::getId, taskId)
                .eq(ApprovalTask::getStatus, ApprovalTaskStatus.READY)
                .set(ApprovalTask::getStatus, targetStatus)
                .set(ApprovalTask::getDecidedBy, decidedBy)
                .set(ApprovalTask::getDecidedAt, now)
                .set(ApprovalTask::getDecisionComment, decisionComment)
                .set(ApprovalTask::getUpdateTime, now)
                .set(ApprovalTask::getUpdateUserId, decidedBy));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "审批任务状态已变更，请刷新后重试");
        }
        return taskMapper.selectById(taskId);
    }

    /**
     * 取消单据全部 WAITING/READY 任务，供修改后重新审核或系统终止流程使用。
     * operatorId 可空；已批准、已驳回和已取消的历史任务保持不变。
     */
    @Transactional(rollbackFor = Exception.class)
    public int cancelOpenTasks(Long documentId, Long operatorId, String reason) {
        Instant now = now();
        return taskMapper.update(null, Wrappers.<ApprovalTask>lambdaUpdate()
                .eq(ApprovalTask::getDocumentId, documentId)
                .in(ApprovalTask::getStatus, ApprovalTaskStatus.WAITING, ApprovalTaskStatus.READY)
                .set(ApprovalTask::getStatus, ApprovalTaskStatus.CANCELLED)
                .set(ApprovalTask::getDecidedBy, operatorId)
                .set(ApprovalTask::getDecidedAt, now)
                .set(ApprovalTask::getDecisionComment, reason)
                .set(ApprovalTask::getUpdateTime, now)
                .set(ApprovalTask::getUpdateUserId, operatorId));
    }

    /**
     * 按 (documentId, requestKey) 幂等追加事件；重复请求返回首次写入的事件。
     * 系统事件由调用方将 actorType 设为 SYSTEM，actorId 可为 null。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentEvent appendEvent(DocumentEvent event) {
        DocumentEvent existing = findEvent(event.getDocumentId(), event.getRequestKey());
        if (existing != null) {
            return existing;
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(now());
        }
        try {
            eventMapper.insert(event);
            return event;
        } catch (DuplicateKeyException e) {
            DocumentEvent concurrent = findEvent(event.getDocumentId(), event.getRequestKey());
            if (concurrent != null) {
                log.info("[物料流程事件] 重复请求保留首次事件：单据ID={}, requestKey={}",
                        event.getDocumentId(), event.getRequestKey());
                return concurrent;
            }
            throw e;
        }
    }

    /** 查询单据全部事件，按发生时间和主键升序。 */
    public List<DocumentEvent> listEvents(Long documentId) {
        return eventMapper.selectList(Wrappers.<DocumentEvent>lambdaQuery()
                .eq(DocumentEvent::getDocumentId, documentId)
                .orderByAsc(DocumentEvent::getOccurredAt)
                .orderByAsc(DocumentEvent::getId));
    }

    private DocumentEvent findEvent(Long documentId, String requestKey) {
        return eventMapper.selectOne(Wrappers.<DocumentEvent>lambdaQuery()
                .eq(DocumentEvent::getDocumentId, documentId)
                .eq(DocumentEvent::getRequestKey, requestKey)
                .last("LIMIT 1"));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
