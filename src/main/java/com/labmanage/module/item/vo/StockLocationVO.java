package com.labmanage.module.item.vo;

import java.time.Instant;
import java.util.List;

public record StockLocationVO(
        String id,
        String locationCode,
        String locationName,
        String description,
        Integer enabled,
        List<StockLocationAdminVO> admins,
        Instant createTime,
        Instant updateTime
) {
}
