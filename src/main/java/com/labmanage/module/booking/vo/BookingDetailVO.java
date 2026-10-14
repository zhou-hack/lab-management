package com.labmanage.module.booking.vo;

import com.labmanage.module.booking.enums.BookingStatus;
import com.labmanage.module.booking.enums.CancelType;

import java.time.Instant;

/**
 * 预约详情（审批页抽屉/详情弹窗）。
 * 附驳回/取消留痕与占用释放信息，便于追溯。
 */
public record BookingDetailVO(
        String id,
        String spaceId,
        String spaceName,
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
        Instant updateTime
) {
}
