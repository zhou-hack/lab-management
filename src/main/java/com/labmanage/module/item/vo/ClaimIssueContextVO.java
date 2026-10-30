package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.AssetStatus;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.QuantityMode;
import com.labmanage.module.item.enums.PickupMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ClaimIssueContextVO(
        String claimId,
        Integer revisionNo,
        String locationId,
        String locationCode,
        String locationName,
        Instant bookingStartAt,
        Instant bookingEndAt,
        Instant issueEarliestAt,
        boolean canIssue,
        PickupMode pickupMode,
        String pickupUserId,
        String pickupUserName,
        String issueBlockedReason,
        List<String> riskWarnings,
        List<Line> lines
) {
    public record Line(
            String claimLineId,
            String materialId,
            String materialName,
            String specification,
            MaterialType materialType,
            QuantityMode quantityMode,
            String baseUnit,
            BigDecimal requestedQuantity,
            BigDecimal issuedQuantity,
            BigDecimal remainingQuantity,
            List<Position> positions,
            List<Asset> candidateAssets,
            List<Reservation> reservations
    ) {
    }

    public record Position(
            String positionText,
            BigDecimal ownedQuantity,
            BigDecimal availableQuantity
    ) {
    }

    public record Asset(
            String fixedAssetId,
            String assetCode,
            String locationId,
            String positionText,
            AssetStatus status,
            boolean reservedByThisClaim
    ) {
    }

    public record Reservation(
            String reservationId,
            String allocationId,
            String locationId,
            String locationCode,
            String locationName,
            String fixedAssetId,
            String assetCode,
            String reservationRule,
            BigDecimal reservedQuantity,
            BigDecimal consumedQuantity,
            BigDecimal releasedQuantity,
            BigDecimal remainingQuantity,
            Instant reservationStartAt,
            Instant reservationEndAt
    ) {
    }
}
