package com.labmanage.module.booking.flow.service;

import com.labmanage.module.booking.entity.LabBooking;
import com.labmanage.module.booking.entity.LabBookingSeries;
import com.labmanage.module.booking.flow.entity.ApprovalTask;
import com.labmanage.module.booking.flow.entity.BizDocument;
import com.labmanage.module.booking.flow.entity.DocumentEvent;
import com.labmanage.module.booking.flow.enums.ActorType;
import com.labmanage.module.booking.flow.enums.ApprovalTaskStatus;
import com.labmanage.module.booking.flow.enums.DocumentEventType;
import com.labmanage.module.booking.flow.mapper.ApprovalTaskMapper;
import com.labmanage.module.booking.flow.mapper.BizDocumentMapper;
import com.labmanage.module.booking.flow.mapper.DocumentEventMapper;
import com.labmanage.module.booking.flow.vo.BookingTimelineEventVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场地预约权级流程编排：统一单据（biz_document）、审批阶段任务
 * （approval_task）、不可变事件流（document_event）。
 * <p>
 * 与 lab_booking 一一对应：document_type=LAB_BOOKING、document_no="LAB"+预约ID。
 * 事件按 (document_id, request_key) 幂等，requestKey="事件类型:预约ID"，
 * 保证每个流程动作对同一预约仅落一行（定时任务并发/重试安全）。
 */
@Service
public class BookingFlowService {

    private static final Logger log = LoggerFactory.getLogger(BookingFlowService.class);

    /** 场地预约本期单阶段：管理员核对（待管理员核对 → 已批准/整单驳回） */
    public static final String STAGE_ADMIN_REVIEW = "ADMIN_REVIEW";
    public static final String REQUIRED_ROLE_ADMIN = "ADMIN";
    public static final String DOCUMENT_TYPE_LAB_BOOKING = "LAB_BOOKING";

    /** 周期预约规则单据类型与单号前缀（document_no="LBS"+规则ID） */
    public static final String DOCUMENT_TYPE_LAB_BOOKING_SERIES = "LAB_BOOKING_SERIES";

    /** 申请人缺省归属部门：设备处（sys_rbac.sql 预置 id=1） */
    public static final long DEFAULT_DEPARTMENT_ID = 1L;

    private final BizDocumentMapper documentMapper;
    private final ApprovalTaskMapper taskMapper;
    private final DocumentEventMapper eventMapper;
    private final SysUserMapper userMapper;

    public BookingFlowService(BizDocumentMapper documentMapper,
                              ApprovalTaskMapper taskMapper,
                              DocumentEventMapper eventMapper,
                              SysUserMapper userMapper) {
        this.documentMapper = documentMapper;
        this.taskMapper = taskMapper;
        this.eventMapper = eventMapper;
        this.userMapper = userMapper;
    }

    public static String documentNoOf(Long bookingId) {
        return "LAB" + bookingId;
    }

    public static String documentNoOfSeries(Long seriesId) {
        return "LBS" + seriesId;
    }

    /**
     * 预约提交时建档：幂等创建统一单据 + READY 管理员核对任务 + SUBMIT 事件。
     * 已存在则原样返回（重复提交保护）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BizDocument onSubmit(LabBooking booking, Long applicantDepartmentId) {
        BizDocument doc = documentMapper.selectByDocumentNo(documentNoOf(booking.getId()));
        if (doc != null) {
            return doc;
        }
        Instant now = Instant.now();
        doc = new BizDocument();
        doc.setDocumentNo(documentNoOf(booking.getId()));
        doc.setDocumentType(DOCUMENT_TYPE_LAB_BOOKING);
        doc.setOwnerUserId(booking.getApplicantId());
        doc.setDepartmentId(applicantDepartmentId == null ? DEFAULT_DEPARTMENT_ID : applicantDepartmentId);
        doc.setSubmittedAt(booking.getCreateTime() == null ? now : booking.getCreateTime());
        doc.setIdempotencyKey(String.valueOf(booking.getId()));
        documentMapper.insert(doc);

        ApprovalTask task = new ApprovalTask();
        task.setDocumentId(doc.getId());
        task.setStageCode(STAGE_ADMIN_REVIEW);
        task.setSequenceNo(1);
        task.setRoundNo(1);
        task.setRequiredRole(REQUIRED_ROLE_ADMIN);
        task.setStatus(ApprovalTaskStatus.READY);
        task.setAvailableAt(now);
        // 超时点 = 预约开始时间：start_time 已过仍未核对，系统自动取消
        task.setTimeoutAt(booking.getStartAt());
        task.setTimeoutAction("CANCEL");
        taskMapper.insert(task);

        appendEvent(doc.getId(), booking.getId(), DocumentEventType.SUBMIT,
                null, "PENDING", booking.getApplicantId(), ActorType.USER,
                now, booking.getPurpose(), task.getId());
        log.info("[流程建档] 预约ID={}, 单据ID={}, 审批任务ID={}, 阶段=ADMIN_REVIEW/READY",
                booking.getId(), doc.getId(), task.getId());
        return doc;
    }

    /** 管理员核对通过：任务 APPROVED + APPROVE 事件。 */
    @Transactional(rollbackFor = Exception.class)
    public void onApprove(BizDocument doc, LabBooking booking, Long adminId) {
        ApprovalTask task = requireReviewTask(doc.getId());
        Instant now = Instant.now();
        task.setStatus(ApprovalTaskStatus.APPROVED);
        task.setDecidedBy(adminId);
        task.setDecidedAt(now);
        task.setDecisionComment("核对通过，批准使用");
        taskMapper.updateById(task);

        appendEvent(doc.getId(), booking.getId(), DocumentEventType.APPROVE,
                "PENDING", "APPROVED", adminId, ActorType.USER,
                now, task.getDecisionComment(), task.getId());
    }

    /** 管理员整单驳回：任务 REJECTED + REJECT 事件（原因必填，详情可见）。 */
    @Transactional(rollbackFor = Exception.class)
    public void onReject(BizDocument doc, LabBooking booking, Long adminId, String reason) {
        ApprovalTask task = requireReviewTask(doc.getId());
        Instant now = Instant.now();
        task.setStatus(ApprovalTaskStatus.REJECTED);
        task.setDecidedBy(adminId);
        task.setDecidedAt(now);
        task.setDecisionComment(reason);
        taskMapper.updateById(task);

        appendEvent(doc.getId(), booking.getId(), DocumentEventType.REJECT,
                "PENDING", "REJECTED", adminId, ActorType.USER,
                now, reason, task.getId());
    }

    /**
     * 撤销/超时取消：任务 CANCELLED + 事件。
     *
     * @param system true=定时任务超时自动取消（TIMEOUT_CANCEL/SYSTEM）；
     *               false=申请人本人撤销（CANCEL/USER）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onCancel(BizDocument doc, LabBooking booking, Long actorId, boolean system, String reason) {
        ApprovalTask task = requireReviewTask(doc.getId());
        Instant now = Instant.now();
        task.setStatus(ApprovalTaskStatus.CANCELLED);
        if (!system) {
            task.setDecidedBy(actorId);
        }
        task.setDecidedAt(now);
        task.setDecisionComment(reason);
        taskMapper.updateById(task);

        DocumentEventType type = system ? DocumentEventType.TIMEOUT_CANCEL : DocumentEventType.CANCEL;
        ActorType actorType = system ? ActorType.SYSTEM : ActorType.USER;
        appendEvent(doc.getId(), booking.getId(), type,
                booking.getStatus() == null ? null : booking.getStatus().name(),
                "CANCELLED", system ? null : actorId, actorType,
                now, reason, task.getId());
    }

    /** 开门签到：CHECKIN 事件（to_status=IN_USE 派生态，不落 lab_booking 状态）。 */
    @Transactional(rollbackFor = Exception.class)
    public void onCheckin(BizDocument doc, LabBooking booking, Long adminId, Instant now) {
        appendEvent(doc.getId(), booking.getId(), DocumentEventType.CHECKIN,
                "APPROVED", "IN_USE", adminId, ActorType.USER, now, "开门签到", null);
    }

    /** 结束清点：CHECKOUT 事件（to_status=FINISHED 派生态）。 */
    @Transactional(rollbackFor = Exception.class)
    public void onCheckout(BizDocument doc, LabBooking booking, Long adminId, Instant now, String note) {
        appendEvent(doc.getId(), booking.getId(), DocumentEventType.CHECKOUT,
                "IN_USE", "FINISHED", adminId, ActorType.USER, now, note, null);
    }

    /** 查询单据；不存在返回 null（历史预约可能无流程档案）。 */
    public BizDocument findByBookingId(Long bookingId) {
        return documentMapper.selectByDocumentNo(documentNoOf(bookingId));
    }

    /** 查询周期预约规则单据；不存在返回 null。 */
    public BizDocument findBySeriesId(Long seriesId) {
        return documentMapper.selectByDocumentNo(documentNoOfSeries(seriesId));
    }

    // ------------------------------------------------------------ 周期预约流程

    /**
     * 周期预约提交时建档：LBS 单据 + READY 管理员核对任务 + SUBMIT 事件。
     * 已存在则原样返回（重复提交保护）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BizDocument onSeriesSubmit(LabBookingSeries series, Long applicantDepartmentId) {
        BizDocument doc = documentMapper.selectByDocumentNo(documentNoOfSeries(series.getId()));
        if (doc != null) {
            return doc;
        }
        Instant now = Instant.now();
        doc = new BizDocument();
        doc.setDocumentNo(documentNoOfSeries(series.getId()));
        doc.setDocumentType(DOCUMENT_TYPE_LAB_BOOKING_SERIES);
        doc.setOwnerUserId(series.getApplicantId());
        doc.setDepartmentId(applicantDepartmentId == null ? DEFAULT_DEPARTMENT_ID : applicantDepartmentId);
        doc.setSubmittedAt(series.getCreateTime() == null ? now : series.getCreateTime());
        doc.setIdempotencyKey(series.getIdempotencyKey());
        documentMapper.insert(doc);

        ApprovalTask task = new ApprovalTask();
        task.setDocumentId(doc.getId());
        task.setStageCode(STAGE_ADMIN_REVIEW);
        task.setSequenceNo(1);
        task.setRoundNo(1);
        task.setRequiredRole(REQUIRED_ROLE_ADMIN);
        task.setStatus(ApprovalTaskStatus.READY);
        task.setAvailableAt(now);
        // 周期规则超时由定时任务按 cycle_end_date 整单取消，此处无需设置超时点
        task.setTimeoutAction("CANCEL");
        taskMapper.insert(task);

        appendEvent(doc.getId(), series.getId(), DocumentEventType.SUBMIT,
                null, "PENDING", series.getApplicantId(), ActorType.USER,
                now, series.getPurpose(), task.getId());
        log.info("[周期流程建档] 规则ID={}, 单据ID={}, 审批任务ID={}, 阶段=ADMIN_REVIEW/READY",
                series.getId(), doc.getId(), task.getId());
        return doc;
    }

    /** 管理员批准周期规则：任务 APPROVED + APPROVE 事件。 */
    @Transactional(rollbackFor = Exception.class)
    public void onSeriesApprove(BizDocument doc, LabBookingSeries series, Long adminId) {
        ApprovalTask task = requireReviewTask(doc.getId());
        Instant now = Instant.now();
        task.setStatus(ApprovalTaskStatus.APPROVED);
        task.setDecidedBy(adminId);
        task.setDecidedAt(now);
        task.setDecisionComment("周期预约核对通过，批准使用");
        taskMapper.updateById(task);

        appendEvent(doc.getId(), series.getId(), DocumentEventType.APPROVE,
                "PENDING", "APPROVED", adminId, ActorType.USER,
                now, task.getDecisionComment(), task.getId());
    }

    /** 管理员整单驳回周期规则：任务 REJECTED + REJECT 事件（原因必填）。 */
    @Transactional(rollbackFor = Exception.class)
    public void onSeriesReject(BizDocument doc, LabBookingSeries series, Long adminId, String reason) {
        ApprovalTask task = requireReviewTask(doc.getId());
        Instant now = Instant.now();
        task.setStatus(ApprovalTaskStatus.REJECTED);
        task.setDecidedBy(adminId);
        task.setDecidedAt(now);
        task.setDecisionComment(reason);
        taskMapper.updateById(task);

        appendEvent(doc.getId(), series.getId(), DocumentEventType.REJECT,
                "PENDING", "REJECTED", adminId, ActorType.USER,
                now, reason, task.getId());
    }

    /** 撤销/超时取消周期规则：任务 CANCELLED + CANCEL/TIMEOUT_CANCEL 事件。 */
    @Transactional(rollbackFor = Exception.class)
    public void onSeriesCancel(BizDocument doc, LabBookingSeries series, Long actorId, boolean system, String reason) {
        ApprovalTask task = requireReviewTask(doc.getId());
        Instant now = Instant.now();
        task.setStatus(ApprovalTaskStatus.CANCELLED);
        if (!system) {
            task.setDecidedBy(actorId);
        }
        task.setDecidedAt(now);
        task.setDecisionComment(reason);
        taskMapper.updateById(task);

        DocumentEventType type = system ? DocumentEventType.TIMEOUT_CANCEL : DocumentEventType.CANCEL;
        appendEvent(doc.getId(), series.getId(), type,
                series.getStatus() == null ? null : series.getStatus().name(),
                "CANCELLED", system ? null : actorId,
                system ? ActorType.SYSTEM : ActorType.USER,
                now, reason, task.getId());
    }

    /**
     * 周期规则批准后逐周生成的预约单流程建档（幂等）：这些单创建即 APPROVED，
     * 需补齐 biz_document + APPROVED 任务 + SUBMIT/APPROVE 事件，使详情时间线、
     * 开门签到/结束清点与单次预约行为一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onGeneratedApproved(LabBooking booking, Long operatorId, Long departmentId) {
        BizDocument doc = documentMapper.selectByDocumentNo(documentNoOf(booking.getId()));
        if (doc != null) {
            return;
        }
        Instant now = Instant.now();
        doc = new BizDocument();
        doc.setDocumentNo(documentNoOf(booking.getId()));
        doc.setDocumentType(DOCUMENT_TYPE_LAB_BOOKING);
        doc.setOwnerUserId(booking.getApplicantId());
        doc.setDepartmentId(departmentId == null ? DEFAULT_DEPARTMENT_ID : departmentId);
        doc.setSubmittedAt(booking.getCreateTime() == null ? now : booking.getCreateTime());
        doc.setIdempotencyKey(String.valueOf(booking.getId()));
        documentMapper.insert(doc);

        ApprovalTask task = new ApprovalTask();
        task.setDocumentId(doc.getId());
        task.setStageCode(STAGE_ADMIN_REVIEW);
        task.setSequenceNo(1);
        task.setRoundNo(1);
        task.setRequiredRole(REQUIRED_ROLE_ADMIN);
        task.setStatus(ApprovalTaskStatus.APPROVED);
        task.setAvailableAt(now);
        task.setDecidedBy(operatorId);
        task.setDecidedAt(now);
        task.setDecisionComment("周期预约一次批准，本单随规则生效");
        task.setTimeoutAction("NONE");
        taskMapper.insert(task);

        appendEvent(doc.getId(), booking.getId(), DocumentEventType.SUBMIT,
                null, "PENDING", booking.getApplicantId(), ActorType.USER,
                now, "周期预约批量生成", task.getId());
        appendEvent(doc.getId(), booking.getId(), DocumentEventType.APPROVE,
                "PENDING", "APPROVED", operatorId, ActorType.USER,
                now, "周期预约一次批准，本单随规则生效", task.getId());
        log.info("[生成单流程建档] 预约ID={}, 单据ID={}, 审批任务ID={}",
                booking.getId(), doc.getId(), task.getId());
    }

    /** 是否已记录某事件（签到/清点状态判定）。 */
    public boolean hasEvent(Long documentId, DocumentEventType type) {
        if (documentId == null) {
            return false;
        }
        return eventMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentEvent>()
                .eq(DocumentEvent::getDocumentId, documentId)
                .eq(DocumentEvent::getEventType, type)) > 0;
    }

    /** 详情流程时间线（含操作人姓名与事件中文名），按发生时间升序。 */
    public List<BookingTimelineEventVO> timeline(Long bookingId) {
        BizDocument doc = findByBookingId(bookingId);
        if (doc == null) {
            return List.of();
        }
        List<DocumentEvent> events = eventMapper.listByDocument(doc.getId());
        Map<Long, String> actorNames = loadActorNames(events);
        List<BookingTimelineEventVO> result = new ArrayList<>(events.size());
        for (DocumentEvent e : events) {
            String actorName = e.getActorType() == ActorType.SYSTEM
                    ? "系统"
                    : actorNames.getOrDefault(e.getActorId(),
                    e.getActorId() == null ? "系统" : String.valueOf(e.getActorId()));
            result.add(new BookingTimelineEventVO(
                    e.getEventType().name(),
                    e.getEventType().getDisplayName(),
                    e.getActorType().name(),
                    actorName,
                    e.getFromStatus(),
                    e.getToStatus(),
                    e.getOccurredAt(),
                    e.getReason()
            ));
        }
        return result;
    }

    /** 批量取预约的签到/清点时间：bookingId → (CHECKIN/CHECKOUT → 时间)。 */
    public Map<Long, Map<String, Instant>> loadCheckTimes(List<Long> bookingIds) {
        Map<Long, Map<String, Instant>> result = new HashMap<>();
        if (bookingIds == null || bookingIds.isEmpty()) {
            return result;
        }
        List<String> nos = bookingIds.stream().map(BookingFlowService::documentNoOf).toList();
        for (Map<String, Object> raw : eventMapper.selectCheckEvents(nos)) {
            // 兼容 H2（DATABASE_TO_LOWER 列名小写）与 MySQL：统一归一为小写键
            Map<String, Object> row = new HashMap<>();
            raw.forEach((k, v) -> row.put(k == null ? null : k.toLowerCase(), v));
            Object noObj = row.get("documentno");
            Object typeObj = row.get("eventtype");
            Object atObj = row.get("occurredat");
            if (noObj == null || typeObj == null || atObj == null) {
                continue;
            }
            Long bookingId = Long.parseLong(String.valueOf(noObj).substring(3));
            Instant at = toInstant(atObj);
            result.computeIfAbsent(bookingId, k -> new HashMap<>())
                    .put(String.valueOf(typeObj), at);
        }
        return result;
    }

    /**
     * 原始 JDBC 时间值 → Instant（occurred_at 以 UTC 墙钟时间存储）。
     * 兼容 Instant / Timestamp / LocalDateTime / ISO 字符串（H2 与 MySQL 驱动差异）。
     */
    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.util.Date d) {
            return d.toInstant();
        }
        String s = String.valueOf(value).replace(' ', 'T');
        if (s.length() == 19 || (s.length() > 19 && !s.endsWith("Z") && !s.contains("+"))) {
            return java.time.LocalDateTime.parse(s.length() > 23 ? s.substring(0, 23) : s)
                    .toInstant(java.time.ZoneOffset.UTC);
        }
        return Instant.parse(s);
    }

    // ------------------------------------------------------------------ 内部

    private ApprovalTask requireReviewTask(Long documentId) {
        ApprovalTask task = taskMapper.selectByDocumentStage(documentId, STAGE_ADMIN_REVIEW);
        if (task == null) {
            throw new IllegalStateException("审批任务缺失：documentId=" + documentId);
        }
        return task;
    }

    /**
     * 幂等追加事件：先按 (document, requestKey) 查重，再插入；
     * 撞唯一键（并发/重试）时吞掉 DuplicateKeyException 保留首次事件。
     */
    private void appendEvent(Long documentId, Long bookingId, DocumentEventType type,
                             String fromStatus, String toStatus, Long actorId, ActorType actorType,
                             Instant occurredAt, String reason, Long taskId) {
        String requestKey = type.name() + ":" + bookingId;
        boolean exists = eventMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentEvent>()
                        .eq(DocumentEvent::getDocumentId, documentId)
                        .eq(DocumentEvent::getRequestKey, requestKey)) > 0;
        if (exists) {
            log.warn("[流程事件] 已存在，跳过重复写入：单据ID={}, 事件={}, requestKey={}",
                    documentId, type, requestKey);
            return;
        }
        DocumentEvent event = new DocumentEvent();
        event.setDocumentId(documentId);
        event.setEventType(type);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setActorId(actorId);
        event.setActorType(actorType);
        event.setOccurredAt(occurredAt);
        event.setReason(reason);
        event.setTaskId(taskId);
        event.setRequestKey(requestKey);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            log.warn("[流程事件] 唯一键冲突，保留首次事件：单据ID={}, requestKey={}", documentId, requestKey);
        }
    }

    private Map<Long, String> loadActorNames(List<DocumentEvent> events) {
        List<Long> ids = events.stream()
                .filter(e -> e.getActorType() == ActorType.USER && e.getActorId() != null)
                .map(DocumentEvent::getActorId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (SysUser user : userMapper.selectBatchIds(ids)) {
            names.put(user.getId(), user.getRealName());
        }
        return names;
    }

    /** 供定时任务在独立事务中取消单条超时预约（失败不影响整批）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onCancelInNewTx(BizDocument doc, LabBooking booking, String reason) {
        onCancel(doc, booking, null, true, reason);
    }
}
