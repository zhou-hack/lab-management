package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.MaterialType;

import java.math.BigDecimal;

public record MaterialPlanLineVO(
        String id,
        String materialId,
        String materialName,
        String specification,
        MaterialType materialType,
        String baseUnit,
        BigDecimal defaultQuantity
) {
}
