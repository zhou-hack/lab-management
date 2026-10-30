package com.labmanage.module.item.vo;

import java.math.BigDecimal;

public record AssetSummaryVO(
        String materialId,
        String materialName,
        String specification,
        String baseUnit,
        BigDecimal ownedQuantity,
        BigDecimal availableQuantity
) {
}
