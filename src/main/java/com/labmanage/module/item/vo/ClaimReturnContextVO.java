package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClaimReturnContextVO(
        String sourceDocumentId,
        String sourceDocumentNo,
        String claimId,
        String businessChainId,
        String locationId,
        String locationCode,
        String locationName,
        Instant returnDeadline,
        boolean overdue,
        boolean canReturn,
        String returnBlockedReason,
        List<Line> lines
) {
    public record Line(
            String sourceMaterialDocumentLineId,
            String claimLineId,
            String materialId,
            String materialName,
            String specification,
            MaterialType materialType,
            String baseUnit,
            String fixedAssetId,
            String assetCode,
            String sourcePositionText,
            BigDecimal issuedQuantity,
            BigDecimal returnedQuantity,
            BigDecimal settledQuantity,
            BigDecimal remainingQuantity
    ) {
    }
}
