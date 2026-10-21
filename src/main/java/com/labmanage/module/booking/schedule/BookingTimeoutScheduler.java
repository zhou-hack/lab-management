package com.labmanage.module.booking.schedule;

import com.labmanage.module.booking.service.BookingService;
import com.labmanage.module.booking.service.BookingSeriesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 场地预约超时未核对自动取消定时任务。
 * <p>
 * 每 10 分钟扫描一次：start_time 已过仍处于 PENDING（待管理员核对）的预约，
 * 自动流转 CANCELLED(AUTO_EXPIRED) 并写 TIMEOUT_CANCEL 事件；同时扫描周期
 * 已结束仍未核对的 PENDING 周期预约规则整单取消。
 * 单条独立事务失败不阻断整批。应用启动后延迟 1 分钟首次执行。
 */
@Component
public class BookingTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingTimeoutScheduler.class);

    private final BookingService bookingService;
    private final BookingSeriesService seriesService;

    public BookingTimeoutScheduler(BookingService bookingService, BookingSeriesService seriesService) {
        this.bookingService = bookingService;
        this.seriesService = seriesService;
    }

    /** 固定延迟：上一轮结束后 10 分钟再扫；initialDelay 避开启动高峰。 */
    @Scheduled(fixedDelayString = "${lab.booking.timeout-scan-delay:600000}",
            initialDelayString = "${lab.booking.timeout-scan-initial:60000}")
    public void autoCancelExpiredBookings() {
        try {
            int cancelled = bookingService.autoCancelExpired();
            int seriesCancelled = seriesService.autoCancelExpired();
            if (cancelled > 0 || seriesCancelled > 0) {
                log.info("[定时任务] 超时未核对自动取消完成：单次预约 {} 条，周期预约 {} 条", cancelled, seriesCancelled);
            }
        } catch (Exception e) {
            // 调度方法内异常不能抛出，否则 Scheduled 任务将停止后续调度
            log.error("[定时任务] 超时未核对预约扫描异常：{}", e.getMessage(), e);
        }
    }
}
