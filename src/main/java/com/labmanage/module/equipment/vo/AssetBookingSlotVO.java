package com.labmanage.module.equipment.vo;

import com.labmanage.module.equipment.enums.BookingStatus;

import java.time.Instant;

/**
 * 设备未来/进行中的被约时段（设备目录「被约时间」明细）
 */
public record AssetBookingSlotVO(
        String id,
        Instant startAt,
        Instant endAt,
        BookingStatus status
) {
}
