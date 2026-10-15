package com.labmanage.module.equipment.vo;

import java.math.BigDecimal;

/**
 * 设备型号视图对象
 */
public record ModelVO(
        String id,
        String categoryId,
        String categoryName,
        String modelCode,
        String equipmentName,
        String brand,
        String specification,
        String unitName,
        BigDecimal referencePrice,
        Integer expectedLifeMonths,
        Boolean requiresDataErasure,
        Integer enabled
) {
}
