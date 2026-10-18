package com.labmanage.module.booking.vo;

import com.labmanage.module.booking.enums.BookingStatus;
import com.labmanage.module.booking.enums.CancelType;
import com.labmanage.module.booking.flow.vo.BookingTimelineEventVO;

import java.time.Instant;
import java.util.List;

/**
 * 预约详情（审批页抽屉/详情弹窗）。
 * 附驳回/取消留痕、签到/清点时间与完整流程时间线，便于追溯。
 */
public record BookingDetailVO(
        String id,
        String spaceId,
        String labName,
        String purpose,
        Integer participantCount,
        Instant startAt,
        Instant endAt,
        BookingStatus status,
        String statusName,
        String runtimePhase,
        String rejectReason,
        String cancelReason,
        CancelType cancelType,
        String cancelTypeName,
        Instant cancelledAt,
        String applicantId,
        String applicantName,
        Instant createTime,
        Instant updateTime,
        Instant checkinAt,
        Instant checkoutAt,
        List<BookingTimelineEventVO> timeline
) {
}
