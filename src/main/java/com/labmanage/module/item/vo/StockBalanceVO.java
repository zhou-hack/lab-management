package com.labmanage.module.item.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record StockBalanceVO(
        String id,
        String materialId,
        String materialName,
        String specification,
        String baseUnit,
        String locationId,
        String locationCode,
        String locationName,
        String positionText,
        BigDecimal ownedQuantity,
        BigDecimal loanedQuantity,
        BigDecimal availableQuantity,
        Integer version,
        Instant updateTime
) {
}
