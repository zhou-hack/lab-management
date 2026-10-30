package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.ClaimStatus;

import java.time.Instant;
import java.time.LocalDate;

public record ClaimSummaryVO(
        String id,
        String bizDocumentId,
        String businessChainId,
        String documentNo,
        String applicantId,
        String applicantName,
        String purpose,
        LocalDate bookingDate,
        Instant bookingStartAt,
        Instant bookingEndAt,
        Instant returnDeadline,
        ClaimStatus status,
        Integer revisionNo,
        Instant delegatedAt,
        Instant createTime
) {
}
