package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMovementVO(
        String id,
        String movementKey,
        String movementType,
        String direction,
        String materialId,
        String materialName,
        MaterialType materialType,
        String baseUnit,
        String fixedAssetId,
        String assetCode,
        String locationId,
        String locationCode,
        String locationName,
        BigDecimal quantity,
        String materialDocumentId,
        String documentNo,
        String materialDocumentLineId,
        String operatorId,
        String operatorName,
        Instant occurredAt,
        BigDecimal beforeOwnedQuantity,
        BigDecimal afterOwnedQuantity,
        BigDecimal beforeAvailableQuantity,
        BigDecimal afterAvailableQuantity
) {
}
