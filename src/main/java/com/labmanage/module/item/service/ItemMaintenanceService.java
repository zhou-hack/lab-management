package com.labmanage.module.item.service;

import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.entity.MaterialDocument;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ItemMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(ItemMaintenanceService.class);

    private final MaterialClaimMapper claimMapper;
    private final MaterialDocumentMapper documentMapper;
    private final ItemMaintenanceTransactionService transactionService;
    private final ItemTimeService timeService;

    public ItemMaintenanceService(MaterialClaimMapper claimMapper,
                                  MaterialDocumentMapper documentMapper,
                                  ItemMaintenanceTransactionService transactionService,
                                  ItemTimeService timeService) {
        this.claimMapper = claimMapper;
        this.documentMapper = documentMapper;
        this.transactionService = transactionService;
        this.timeService = timeService;
    }

    public int expireClaims() {
        Instant now = timeService.now();
        List<MaterialClaim> candidates = claimMapper.selectExpirationCandidates(timeService.shanghaiDate(now));
        int expired = 0;
        for (MaterialClaim candidate : candidates) {
            try {
                expired += transactionService.expireOne(candidate.getId(), now);
            } catch (Exception e) {
                log.error("[物料申领过期] 单条处理失败，继续扫描：申领ID={}, 错误={}",
                        candidate.getId(), e.getMessage(), e);
            }
        }
        if (expired > 0) {
            log.info("[物料申领过期] 扫描完成：候选={}，已过期={}", candidates.size(), expired);
        }
        return expired;
    }

    public int scanOverdues() {
        Instant now = timeService.now();
        List<MaterialDocument> candidates = documentMapper.selectOverdueNotificationCandidates(now);
        int inserted = 0;
        for (MaterialDocument candidate : candidates) {
            try {
                inserted += transactionService.notifyOne(candidate.getId(), now);
            } catch (Exception e) {
                log.error("[物料逾期通知] 单条处理失败，继续扫描：出库单ID={}, 错误={}",
                        candidate.getId(), e.getMessage(), e);
            }
        }
        if (inserted > 0) {
            log.info("[物料逾期通知] 扫描完成：候选={}，新增通知={}", candidates.size(), inserted);
        }
        return inserted;
    }

    public int scanReturnReminders() {
        Instant now = timeService.now();
        List<Long> candidates = documentMapper.selectReturnReminderClaimIds(now);
        int inserted = 0;
        for (Long claimId : candidates) {
            try {
                inserted += transactionService.notifyReturnReminder(claimId, now);
            } catch (Exception e) {
                log.error("[物料归还提醒] 单条处理失败，继续扫描：申领ID={}, 错误={}", claimId, e.getMessage(), e);
            }
        }
        if (inserted > 0) {
            log.info("[物料归还提醒] 扫描完成：候选={}，新增通知={}", candidates.size(), inserted);
        }
        return inserted;
    }
}
