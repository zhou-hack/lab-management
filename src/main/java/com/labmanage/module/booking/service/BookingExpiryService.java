package com.labmanage.module.booking.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.labmanage.module.booking.entity.LabBooking;
import com.labmanage.module.booking.entity.LabSlot;
import com.labmanage.module.booking.enums.BookingStatus;
import com.labmanage.module.booking.enums.CancelType;
import com.labmanage.module.document.entity.BizDocument;
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
}
