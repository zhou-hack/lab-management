package com.labmanage.module.item.vo;

import java.math.BigDecimal;

public record ClaimAllocationVO(
        String id,
        String claimId,
        String claimLineId,
        Integer revisionNo,
        String materialId,
        String locationId,
        String locationCode,
        String locationName,
        BigDecimal plannedQuantity
) {
}
