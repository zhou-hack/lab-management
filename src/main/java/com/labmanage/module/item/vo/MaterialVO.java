package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.QuantityMode;
import com.labmanage.module.item.enums.TrackMode;

import java.time.Instant;

public record MaterialVO(
        String id,
        MaterialType materialType,
        TrackMode trackMode,
        String skuCode,
        String materialName,
        String specification,
        String baseUnit,
        QuantityMode quantityMode,
        Integer enabled,
        Instant createTime,
        Instant updateTime
) {
}
