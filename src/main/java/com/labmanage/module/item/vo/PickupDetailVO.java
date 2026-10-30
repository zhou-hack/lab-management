package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.ClaimStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PickupDetailVO(
        String id,
        String documentNo,
        String applicantId,
        String applicantName,
        String purpose,
        LocalDate bookingDate,
        Instant bookingStartAt,
        Instant bookingEndAt,
        Instant returnDeadline,
        ClaimStatus status,
        String delegateStudentId,
        Instant delegatedAt,
        List<ClaimLineVO> lines,
        List<ClaimAllocationVO> allocations
) {
}
