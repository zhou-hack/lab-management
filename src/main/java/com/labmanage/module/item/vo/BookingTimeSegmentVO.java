package com.labmanage.module.item.vo;

import java.time.Instant;

public record BookingTimeSegmentVO(
        Integer segmentSeq,
        String startPeriodId,
        String endPeriodId,
        Instant bookingStartAt,
        Instant bookingEndAt
) {
}
