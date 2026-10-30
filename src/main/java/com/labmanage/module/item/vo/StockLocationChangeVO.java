package com.labmanage.module.item.vo;

import java.time.Instant;

public record StockLocationChangeVO(
        String id,
        String locationId,
        String locationCode,
        String locationName,
        String changeType,
        String operatorId,
        String operatorName,
        Instant occurredAt
) {
}
