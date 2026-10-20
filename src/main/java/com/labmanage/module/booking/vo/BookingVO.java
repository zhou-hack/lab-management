package com.labmanage.module.booking.vo;

import com.labmanage.module.booking.enums.BookingStatus;

import java.time.Instant;

/**
 * 预约列表行（「我的预约」与「全部预约」共用）。
 * <p>
 * runtimePhase 为派生展示字段（仅 APPROVED 有值）：UPCOMING 未开始 /
 * IN_USE 使用中 / FINISHED 已结束（按当前时间与预约起止比较，不落库）。
 * checkinAt/checkoutAt 来自 document_event 的开门签到/结束清点记录。
 */
public record BookingVO(
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
        String applicantId,
        String applicantName,
        Instant createTime,
        Instant checkinAt,
        Instant checkoutAt
) {
}
