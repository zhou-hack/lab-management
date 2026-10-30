package com.labmanage.module.item.vo;

import java.math.BigDecimal;

public record ClaimShortageVO(
        String materialId,
        String materialName,
        String locationId,
        String locationCode,
        String locationName,
        BigDecimal requestedQuantity,
        BigDecimal availableQuantity,
        BigDecimal shortageQuantity
) {
}
