package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.QuantityMode;
import com.labmanage.module.item.enums.TrackMode;

public record MaterialOptionVO(
        String id,
        MaterialType materialType,
        TrackMode trackMode,
        String skuCode,
        String materialName,
        String specification,
        String baseUnit,
        QuantityMode quantityMode
) {
}
