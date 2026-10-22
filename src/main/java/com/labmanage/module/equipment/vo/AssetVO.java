package com.labmanage.module.equipment.vo;

import com.labmanage.module.equipment.enums.EquipmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备台账视图对象
 */
public record AssetVO(
        String id,
        String assetCode,
        String serialNo,
        String modelId,
        String modelCode,
        String equipmentName,
        String categoryId,
        String categoryName,
        String departmentId,
        String departmentName,
        String locationDetail,
        String custodianId,
        String custodianName,
        LocalDate purchaseDate,
        BigDecimal purchasePrice,
        LocalDate warrantyUntil,
        EquipmentStatus status,
        Integer enabled
) {
}
