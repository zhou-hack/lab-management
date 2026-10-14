package com.labmanage.module.equipment.dto;

import com.labmanage.module.equipment.enums.ValueLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 设备价值定级请求（定级后记录定级人与时间；未分类设备禁止借用）
 */
public record AssetClassifyRequest(
        @NotNull(message = "价值分级不能为空")
        ValueLevel valueLevel,

        @Size(max = 500, message = "定级理由长度不能超过 500")
        String classificationReason
) {
}
