package com.labmanage.module.equipment.vo;

import com.labmanage.module.equipment.enums.EquipmentStatus;
import com.labmanage.module.equipment.enums.ValueLevel;

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
        String labId,
        String labName,
        String departmentId,
        String departmentName,
        String locationDetail,
        String custodianId,
        String custodianName,
        LocalDate purchaseDate,
        BigDecimal purchasePrice,
        LocalDate warrantyUntil,
        ValueLevel valueLevel,
        EquipmentStatus status,
        Integer enabled,

        /** 占用状态：IN_USE 使用中 / RESERVED 未来被约 / FREE 无预约（按有效预约单实时计算） */
        String usageState
) {
}
