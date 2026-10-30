package com.labmanage.module.item.vo;

import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.QuantityMode;

import java.math.BigDecimal;

public record ClaimTemplateLineVO(
        String materialId,
        String materialName,
        String specification,
        MaterialType materialType,
        QuantityMode quantityMode,
        String baseUnit,
        BigDecimal requestedQuantity
) {
}
