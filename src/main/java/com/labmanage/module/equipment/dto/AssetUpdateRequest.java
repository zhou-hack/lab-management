package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备台账修改请求（编号/型号不可改；迁移用独立接口；状态由业务动作变更）
 */
public record AssetUpdateRequest(
        @Size(max = 255, message = "存放位置长度不能超过 255")
        String locationDetail,

        Long custodianId,

        LocalDate purchaseDate,

        BigDecimal purchasePrice,

        LocalDate warrantyUntil,

        @NotNull(message = "启用状态不能为空")
        Integer enabled
) {
}
