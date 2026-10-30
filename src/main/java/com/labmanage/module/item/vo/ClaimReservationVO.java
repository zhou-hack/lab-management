package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ClaimReservationVO(
        String id,
        String claimId,
        String claimLineId,
        String allocationId,
        Integer revisionNo,
        String materialId,
        String locationId,
        String locationCode,
        String locationName,
        String fixedAssetId,
        String assetCode,
        String reservationRule,
        Integer assetTotalSnapshot,
        BigDecimal reservedQuantity,
        BigDecimal consumedQuantity,
        BigDecimal releasedQuantity,
        BigDecimal remainingQuantity,
        Instant reservationStartAt,
        Instant reservationEndAt,
        ReservationStatus status,
        Instant closedAt
) {
}
