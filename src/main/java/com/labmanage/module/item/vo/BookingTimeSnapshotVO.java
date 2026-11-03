package com.labmanage.module.item.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BookingTimeSnapshotVO(
        LocalDate bookingDate,
        String startPeriodId,
        String endPeriodId,
        Instant bookingStartAt,
        Instant bookingEndAt,
        Instant returnDeadline,
        Instant issueEarliestAt,
        List<BookingTimeSegmentVO> segments
) {
}
