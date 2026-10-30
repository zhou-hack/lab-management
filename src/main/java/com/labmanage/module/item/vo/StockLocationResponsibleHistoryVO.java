package com.labmanage.module.item.vo;

import java.time.Instant;
import java.util.List;

public record StockLocationResponsibleHistoryVO(
        String id,
        String locationId,
        String locationCode,
        String locationName,
        List<StockLocationAdminVO> beforeAdmins,
        List<StockLocationAdminVO> afterAdmins,
        String operatorId,
        String operatorName,
        Instant occurredAt
) {
}
