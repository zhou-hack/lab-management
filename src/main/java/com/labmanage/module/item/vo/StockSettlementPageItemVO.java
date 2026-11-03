package com.labmanage.module.item.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record StockSettlementPageItemVO(
        String id,
        String materialDocumentId,
        String documentNo,
        String businessScene,
        String sourceMaterialDocumentLineId,
        String materialId,
        String materialName,
        String specification,
        String baseUnit,
        String fixedAssetId,
        String assetCode,
        String settlementType,
        BigDecimal quantity,
        String targetAssetStatus,
        String reason,
        String locationId,
        String locationCode,
        String locationName,
        String operatorId,
        String operatorName,
        String reviewStatus,
        String reviewedBy,
        String reviewedByName,
        Instant reviewedAt,
        Instant occurredAt,
        Instant postedAt
) {
}
