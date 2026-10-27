package com.labmanage.module.booking.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.labmanage.module.booking.entity.LabBooking;
import com.labmanage.module.booking.entity.LabSlot;
import com.labmanage.module.booking.enums.BookingStatus;
import com.labmanage.module.booking.enums.CancelType;
import com.labmanage.module.booking.flow.entity.BizDocument;
import com.labmanage.module.booking.flow.enums.DocumentEventType;
import com.labmanage.module.booking.flow.service.BookingFlowService;
import com.labmanage.module.booking.mapper.LabBookingMapper;
import com.labmanage.module.booking.mapper.LabSlotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 单条超时预约自动取消（独立 Bean、独立事务）。
 * <p>
 * 从定时任务逐条调用：REQUIRES_NEW 保证单条失败回滚不影响整批；
 * 乐观条件 status=PENDING 防与管理员人工审批竞争。
 */
@Service
public class BookingExpiryService {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryService.class);

    private static final String AUTO_CANCEL_REASON = "超过预约开始时间仍未完成核对，系统自动取消";

    private final LabBookingMapper bookingMapper;
    private final LabSlotMapper slotMapper;
    private final BookingFlowService flowService;

    public BookingExpiryService(LabBookingMapper bookingMapper,
                                LabSlotMapper slotMapper,
                                BookingFlowService flowService) {
        this.bookingMapper = bookingMapper;
        this.slotMapper = slotMapper;
        this.flowService = flowService;
    }

    /**
     * 取消单条超时预约；返回 0 表示该单已被人工处理（审批/撤销/驳回），跳过。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int cancelOne(LabBooking booking, Instant now) {
        int rows = bookingMapper.update(null, new LambdaUpdateWrapper<LabBooking>()
                .eq(LabBooking::getId, booking.getId())
                .eq(LabBooking::getStatus, BookingStatus.PENDING)
                .set(LabBooking::getStatus, BookingStatus.CANCELLED)
                .set(LabBooking::getCancelReason, AUTO_CANCEL_REASON)
                .set(LabBooking::getCancelType, CancelType.AUTO_EXPIRED)
                .set(LabBooking::getCancelledAt, now));
        if (rows == 0) {
            return 0;
        }
        // PENDING 单未写占用，无需释放 lab_slot；保险起见幂等释放一次
        slotMapper.update(null, new LambdaUpdateWrapper<LabSlot>()
                .eq(LabSlot::getBookingId, booking.getId())
                .isNull(LabSlot::getReleasedAt)
                .set(LabSlot::getReleasedAt, now)
                .set(LabSlot::getReleaseReason, "超时未批自动取消"));

        BizDocument doc = flowService.findByBookingId(booking.getId());
        if (doc != null) {
            flowService.onCancel(doc, booking, null, true, AUTO_CANCEL_REASON);
        } else {
            log.warn("[超时自动取消] 流程单据缺失，仅流转预约状态：预约ID={}", booking.getId());
        }
        return 1;
    }

    /**
     * 超时自动完结单条已批准预约（A1）：占用滞留兜底。
     * <p>
     * 已有 CHECKOUT 事件（人工已清点）直接跳过；否则幂等释放占用槽并写
     * AUTO_FINISH 事件。主状态保持 APPROVED，不引入第五态；事件按
     * requestKey 幂等，与人工清点/撤销并发时先到者生效、后到者安全跳过。
     *
     * @return 1=本次完结；0=已清点或无占用可释放
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int finishOne(LabBooking booking, Instant now) {
        BizDocument doc = flowService.findByBookingId(booking.getId());
        if (doc != null && flowService.hasEvent(doc.getId(), DocumentEventType.CHECKOUT)) {
            return 0;
        }
        int released = slotMapper.update(null, new LambdaUpdateWrapper<LabSlot>()
                .eq(LabSlot::getBookingId, booking.getId())
                .isNull(LabSlot::getReleasedAt)
                .set(LabSlot::getReleasedAt, now)
                .set(LabSlot::getReleaseReason, "超时未清点自动完结"));
        if (doc == null) {
            log.warn("[超时自动完结] 流程单据缺失，仅释放占用：预约ID={}, 释放行数={}", booking.getId(), released);
        } else {
            flowService.onAutoFinish(doc, booking, now);
        }
        log.info("[超时自动完结] 完成：预约ID={}, 释放占用行数={}, 时间={}", booking.getId(), released, now);
        return 1;
    }
}
