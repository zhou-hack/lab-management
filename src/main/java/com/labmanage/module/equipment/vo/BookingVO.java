package com.labmanage.module.equipment.vo;

import com.labmanage.module.equipment.enums.BookingStatus;
import com.labmanage.module.equipment.enums.ValueLevel;

import java.time.Instant;

/**
 * 器材借用单视图对象
 */
public record BookingVO(
        String id,
        String documentNo,
        String assetId,
        String assetCode,
        String equipmentName,
        String labId,
        String labName,
        String applicantId,
        String applicantName,
        String approvalTeacherId,
        String approvalTeacherName,
        String purpose,
        Instant startAt,
        Instant endAt,
        ValueLevel valueLevelSnapshot,
        BookingStatus status,
        String cancelReason,
        Instant cancelledAt,
        Instant createTime
) {
}
