package com.labmanage.module.item.vo;

import java.time.Instant;

public record ClaimSegmentVO(
        String id,
        String claimId,
        Integer revisionNo,
        Integer segmentSeq,
        String startPeriodId,
        String endPeriodId,
        Instant bookingStartAt,
        Instant bookingEndAt
) {
}
