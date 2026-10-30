package com.labmanage.module.item.vo;

import java.math.BigDecimal;

public record ClaimPlannedAllocationVO(
        String materialId,
        String materialName,
        String locationId,
        String locationCode,
        String locationName,
        BigDecimal plannedQuantity,
        BigDecimal availableQuantity
) {
}
