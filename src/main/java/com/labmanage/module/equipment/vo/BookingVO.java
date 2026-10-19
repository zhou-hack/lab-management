package com.labmanage.module.equipment.vo;

import com.labmanage.module.equipment.enums.BookingStatus;
import com.labmanage.module.equipment.enums.ValueLevel;

import java.time.Instant;

/**
 * 设备申请单视图对象（联查单据号/设备/型号/申请人/审批教师）
 */
public record BookingVO(
        String id,
        String documentNo,
        String assetId,
        String assetCode,
        String equipmentName,
        String modelCode,
        String brand,
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
