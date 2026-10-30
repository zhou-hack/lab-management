package com.labmanage.module.item.schedule;

import com.labmanage.module.item.service.ItemMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ItemMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(ItemMaintenanceScheduler.class);

    private final ItemMaintenanceService maintenanceService;

    public ItemMaintenanceScheduler(ItemMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Scheduled(fixedDelayString = "${lab.item.maintenance-scan-delay:60000}",
            initialDelayString = "${lab.item.maintenance-scan-initial:60000}")
    public void scan() {
        try {
            maintenanceService.expireClaims();
        } catch (Exception e) {
            log.error("[物料定时任务] 申领过期候选扫描异常：{}", e.getMessage(), e);
        }
        try {
            maintenanceService.scanOverdues();
        } catch (Exception e) {
            log.error("[物料定时任务] 逾期通知候选扫描异常：{}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${lab.item.return-reminder-scan-delay:7200000}",
            initialDelayString = "${lab.item.return-reminder-scan-initial:60000}")
    public void scanReturnReminders() {
        try {
            maintenanceService.scanReturnReminders();
        } catch (Exception e) {
            log.error("[物料定时任务] 归还提醒候选扫描异常：{}", e.getMessage(), e);
        }
    }
}
