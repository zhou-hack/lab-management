package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.QuantityMode;

import java.math.BigDecimal;

public record MaterialPlanMaterialVO(
        String materialId,
        String materialName,
        String specification,
        String materialType,
        QuantityMode quantityMode,
        String baseUnit,
        BigDecimal ownedQuantity
) {
}
