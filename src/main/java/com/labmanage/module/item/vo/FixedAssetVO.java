package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.AssetStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FixedAssetVO(
        String id,
        String materialId,
        String materialName,
        String specification,
        String baseUnit,
        String assetCode,
        BigDecimal price,
        String locationId,
        String locationCode,
        String locationName,
        String positionText,
        AssetStatus status,
        List<StockLocationAdminVO> custodians,
        Instant createTime,
        Instant updateTime
) {
}
