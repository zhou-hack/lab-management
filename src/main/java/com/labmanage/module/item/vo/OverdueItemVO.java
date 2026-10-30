package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OverdueItemVO(
        String type,
        String claimId,
        String claimBizDocumentId,
        String claimDocumentNo,
        String sourceDocumentId,
        String sourceBizDocumentId,
        String sourceDocumentNo,
        String returnDocumentId,
        String returnBizDocumentId,
        String returnDocumentNo,
        List<MaterialSummary> materials,
        BigDecimal outstandingQuantity,
        String applicantId,
        String applicantName,
        String locationId,
        String locationCode,
        String locationName,
        Instant sourcePostedAt,
        Instant postedAt,
        Instant returnDeadline,
        Long overdueSeconds,
        String overdueReason
) {
    public record MaterialSummary(
            String materialId,
            String materialName,
            String specification,
            MaterialType materialType,
            String baseUnit,
            BigDecimal outstandingQuantity,
            BigDecimal returnedQuantity,
            BigDecimal settledQuantity
    ) {
    }
}
