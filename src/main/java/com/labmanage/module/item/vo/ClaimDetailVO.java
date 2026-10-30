package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.ClaimStatus;
import com.labmanage.module.item.enums.PickupMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ClaimDetailVO(
        String id,
        String bizDocumentId,
        String businessChainId,
        String documentNo,
        String applicantId,
        String applicantName,
        String purpose,
        LocalDate bookingDate,
        String startPeriodId,
        String endPeriodId,
        Instant bookingStartAt,
        Instant bookingEndAt,
        Instant returnDeadline,
        Instant issueEarliestAt,
        ClaimStatus status,
        Integer revisionNo,
        String approvedBy,
        Instant approvedAt,
        String rejectReason,
        String abandonRequestedBy,
        Instant abandonRequestedAt,
        String abandonReason,
        String closeReason,
        String closedBy,
        Instant closedAt,
        String cancelledBy,
        Instant cancelledAt,
        PickupMode pickupMode,
        String delegateStudentId,
        String delegateStudentName,
        String delegateStudentAccount,
        String delegateGrantId,
        Instant delegatedAt,
        boolean delegationValid,
        String delegationBlockedReason,
        List<ClaimLineVO> lines,
        List<ClaimAllocationVO> allocations,
        List<ClaimReservationVO> reservations,
        List<ItemDocumentEventVO> events,
        boolean canReview,
        boolean canModify,
        boolean canCancel,
        boolean canAbandon,
        boolean canClose,
        boolean canRebook,
        boolean canIssue,
        boolean canDelegate,
        Map<String, String> actionReasons,
        Instant createTime,
        Instant updateTime
) {
}
