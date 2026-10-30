package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.entity.MaterialDocument;
import com.labmanage.module.item.entity.MaterialNotification;
import com.labmanage.module.item.entity.StockLocationAdmin;
import com.labmanage.module.item.enums.ClaimStatus;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper.SourceLineProcessing;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import com.labmanage.module.item.mapper.MaterialNotificationMapper;
import com.labmanage.module.item.mapper.StockLocationAdminMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ItemMaintenanceTransactionService {

    private static final String EXPIRE_REASON = "超过预约当天实发期限仍未实发，系统自动过期";

    private final MaterialClaimMapper claimMapper;
    private final MaterialDocumentMapper documentMapper;
    private final MaterialDocumentLineMapper documentLineMapper;
    private final MaterialNotificationMapper notificationMapper;
    private final StockLocationAdminMapper locationAdminMapper;
    private final ReservationService reservationService;
    private final ItemFlowService flowService;
    private final ItemTimeService timeService;
    private final NotificationService notificationService;

    public ItemMaintenanceTransactionService(MaterialClaimMapper claimMapper,
                                             MaterialDocumentMapper documentMapper,
                                             MaterialDocumentLineMapper documentLineMapper,
                                             MaterialNotificationMapper notificationMapper,
                                             StockLocationAdminMapper locationAdminMapper,
                                             ReservationService reservationService,
                                             ItemFlowService flowService,
                                             ItemTimeService timeService,
                                             NotificationService notificationService) {
        this.claimMapper = claimMapper;
        this.documentMapper = documentMapper;
        this.documentLineMapper = documentLineMapper;
        this.notificationMapper = notificationMapper;
        this.locationAdminMapper = locationAdminMapper;
        this.reservationService = reservationService;
        this.flowService = flowService;
        this.timeService = timeService;
        this.notificationService = notificationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public int expireOne(Long claimId, Instant now) {
        MaterialClaim claim = claimMapper.selectByIdForUpdate(claimId);
        if (claim == null || !isExpirable(claim, now) || hasOutbound(claimId)) {
            return 0;
        }
        ClaimStatus previousStatus = claim.getStatus();
        int updated = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, claimId)
                .eq(MaterialClaim::getRevisionNo, claim.getRevisionNo())
                .eq(MaterialClaim::getStatus, previousStatus)
                .set(MaterialClaim::getStatus, ClaimStatus.EXPIRED)
                .set(MaterialClaim::getUpdateTime, now)
                .set(MaterialClaim::getUpdateUserId, null));
        if (updated != 1) {
            return 0;
        }

        reservationService.releaseForClaim(claimId, null);
        flowService.cancelOpenTasks(claim.getBizDocumentId(), null, EXPIRE_REASON);
        DocumentEvent event = new DocumentEvent();
        event.setId(IdWorker.getId());
        event.setDocumentId(claim.getBizDocumentId());
        event.setEventType("EXPIRE");
        event.setFromStatus(previousStatus.name());
        event.setToStatus(ClaimStatus.EXPIRED.name());
        event.setActorId(null);
        event.setActorType("SYSTEM");
        event.setOccurredAt(now);
        event.setReason(EXPIRE_REASON);
        event.setCreateTime(now);
        event.setUpdateTime(now);
        event.setDeleted(0);
        event.setRequestKey("ITEM_CLAIM:" + claimId + ":" + claim.getRevisionNo() + ":EXPIRE");
        flowService.appendEvent(event);
        return 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public int notifyOne(Long sourceDocumentId, Instant now) {
        MaterialDocument snapshot = documentMapper.selectById(sourceDocumentId);
        if (snapshot == null || snapshot.getBusinessChainId() == null) {
            return 0;
        }
        MaterialClaim claim = claimMapper.selectByIdForUpdate(snapshot.getBusinessChainId());
        if (claim == null) {
            return 0;
        }
        MaterialDocument source = documentMapper.selectByIdForUpdate(sourceDocumentId);
        if (!isOverdueCandidate(source, claim, now)) {
            return 0;
        }
        List<SourceLineProcessing> processing = documentLineMapper.selectProcessingBySourceDocument(sourceDocumentId);
        if (processing.stream().noneMatch(line -> line.remainingQuantity().signum() > 0)) {
            return 0;
        }

        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(claim.getApplicantId());
        locationAdminMapper.selectList(Wrappers.<StockLocationAdmin>lambdaQuery()
                        .eq(StockLocationAdmin::getLocationId, source.getLocationId())
                        .orderByAsc(StockLocationAdmin::getUserId))
                .forEach(admin -> recipients.add(admin.getUserId()));
        LocalDate today = timeService.shanghaiDate(now);
        int inserted = 0;
        for (Long recipientId : recipients) {
            inserted += notifyRecipient(recipientId, claim, source, today, now);
        }
        return inserted;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public int notifyReturnReminder(Long claimId, Instant now) {
        MaterialClaim claim = claimMapper.selectByIdForUpdate(claimId);
        if (claim == null || claim.getBookingEndAt().isAfter(now)
                || documentMapper.countReturnReminderClaim(claimId, now) == 0) {
            return 0;
        }
        Long recipientId = claim.getApplicantId();
        if (!notificationService.canReceive(recipientId, "item:claim:list")) {
            return 0;
        }
        MaterialNotification latest = notificationService.latestByType(recipientId, claimId, "RETURN_REMINDER");
        if (latest == null) {
            return insertReturnReminder(claim, recipientId, "IMMEDIATE", "RETURN_REMINDER:" + claimId + ":INITIAL",
                    "预约结束归还提醒", now);
        }
        if (now.isBefore(latest.getCreateTime().plus(Duration.ofHours(2)))) {
            return 0;
        }
        long slotNo = Math.max(1, Duration.between(claim.getBookingEndAt(), now).toHours() / 2);
        return insertReturnReminder(claim, recipientId, "TWO_HOURLY",
                "RETURN_REMINDER:" + claimId + ":2H:" + slotNo, "每2小时归还提醒", now);
    }

    private int insertReturnReminder(MaterialClaim claim, Long recipientId, String stage,
                                     String dedupeKey, String title, Instant now) {
        MaterialNotification notification = new MaterialNotification();
        notification.setDedupeKey(dedupeKey);
        notification.setRecipientUserId(recipientId);
        notification.setNotificationType("RETURN_REMINDER");
        notification.setNotificationStage(stage);
        notification.setClaimId(claim.getId());
        notification.setBizDocumentId(claim.getBizDocumentId());
        notification.setTitle(title);
        notification.setContent("申领单 " + claim.getId() + " 的预约时间已结束，仍有物料未归还，请及时归还或登记处理。");
        return notificationService.createSystemNotification(notification, now) ? 1 : 0;
    }

    private int notifyRecipient(Long recipientId, MaterialClaim claim, MaterialDocument source,
                                LocalDate today, Instant now) {
        String initialKey = "OVERDUE:" + source.getId() + ":INITIAL";
        MaterialNotification initial = findNotification(recipientId, initialKey);
        if (initial == null) {
            return insertNotification(recipientId, claim, source, "IMMEDIATE", null,
                    initialKey, "申领物料逾期未还",
                    "出库单 " + source.getDocumentNo() + " 已超过归还期限，仍有物料未归还，请及时归还或登记处理。", now);
        }
        if (today.equals(timeService.shanghaiDate(initial.getCreateTime()))) {
            return 0;
        }
        String dailyKey = "OVERDUE:" + source.getId() + ":DAILY:" + today;
        if (findNotification(recipientId, dailyKey) != null) {
            return 0;
        }
        return insertNotification(recipientId, claim, source, "DAILY", today,
                dailyKey, "申领物料逾期每日提醒",
                "出库单 " + source.getDocumentNo() + " 仍有逾期未还物料，请尽快归还或登记处理。", now);
    }

    private int insertNotification(Long recipientId, MaterialClaim claim, MaterialDocument source,
                                   String stage, LocalDate reminderDate, String dedupeKey,
                                   String title, String content, Instant now) {
        MaterialNotification notification = new MaterialNotification();
        notification.setDedupeKey(dedupeKey);
        notification.setRecipientUserId(recipientId);
        notification.setNotificationType("OVERDUE_UNRETURNED");
        notification.setNotificationStage(stage);
        notification.setReminderDate(reminderDate);
        notification.setClaimId(claim.getId());
        notification.setMaterialDocumentId(source.getId());
        notification.setBizDocumentId(source.getBizDocumentId());
        notification.setTitle(title);
        notification.setContent(content);
        return notificationService.createSystemNotification(notification, now) ? 1 : 0;
    }

    private MaterialNotification findNotification(Long recipientId, String dedupeKey) {
        return notificationMapper.selectOne(Wrappers.<MaterialNotification>lambdaQuery()
                .eq(MaterialNotification::getRecipientUserId, recipientId)
                .eq(MaterialNotification::getDedupeKey, dedupeKey)
                .last("LIMIT 1"));
    }

    private boolean isExpirable(MaterialClaim claim, Instant now) {
        return (claim.getStatus() == ClaimStatus.PENDING || claim.getStatus() == ClaimStatus.APPROVED)
                && timeService.isIssueWindowExpired(now, claim.getBookingDate());
    }

    private boolean hasOutbound(Long claimId) {
        return documentMapper.selectCount(Wrappers.<MaterialDocument>lambdaQuery()
                .eq(MaterialDocument::getBusinessChainId, claimId)
                .eq(MaterialDocument::getDocumentType, "OUTBOUND")) > 0;
    }

    private boolean isOverdueCandidate(MaterialDocument source, MaterialClaim claim, Instant now) {
        return source != null
                && claim.getId().equals(source.getBusinessChainId())
                && "OUTBOUND".equals(source.getDocumentType())
                && "CLAIM_ISSUE".equals(source.getBusinessScene())
                && source.getPostedAt() != null
                && source.getLocationId() != null
                && ("UNPROCESSED".equals(source.getProcessStatus()) || "PARTIAL".equals(source.getProcessStatus()))
                && claim.getReturnDeadline().isBefore(now);
    }
}
