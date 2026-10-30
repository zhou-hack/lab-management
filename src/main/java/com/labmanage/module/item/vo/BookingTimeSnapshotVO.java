package com.labmanage.module.item.vo;

import java.time.Instant;
import java.time.LocalDate;

public record BookingTimeSnapshotVO(
        LocalDate bookingDate,
        String startPeriodId,
        String endPeriodId,
        Instant bookingStartAt,
        Instant bookingEndAt,
        Instant returnDeadline,
        Instant issueEarliestAt
) {
}
