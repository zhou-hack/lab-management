package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.QuantityMode;

import java.math.BigDecimal;
import java.time.Instant;

public record ClaimPreviewLineVO(
        String materialId,
        String materialName,
        String specification,
        MaterialType materialType,
        QuantityMode quantityMode,
        String baseUnit,
        BigDecimal requestedQuantity,
        BigDecimal totalAvailableQuantity,
        String reservationRule,
        Instant reservationStartAt,
        Instant reservationEndAt,
        Integer ownedAssetTotal
) {
}
