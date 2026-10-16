package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 设备型号新增请求
 */
public record ModelCreateRequest(
        @NotNull(message = "所属分类不能为空")
        Long categoryId,

        @NotBlank(message = "型号码不能为空")
        @Size(max = 64, message = "型号码长度不能超过 64")
        String modelCode,

        @NotBlank(message = "设备名称不能为空")
        @Size(max = 128, message = "设备名称长度不能超过 128")
        String equipmentName,

        @Size(max = 128, message = "品牌长度不能超过 128")
        String brand,

        @NotBlank(message = "规格说明不能为空")
        @Size(max = 500, message = "规格说明长度不能超过 500")
        String specification,

        @NotNull(message = "数量不能为空")
        @Min(value = 0, message = "数量不能为负")
        Integer quantity,

        @NotBlank(message = "计量单位不能为空")
        @Size(max = 16, message = "计量单位长度不能超过 16")
        String unitName,

        BigDecimal referencePrice,

        Integer expectedLifeMonths,

        /** 归还须数据擦除（缺省 false） */
        Boolean requiresDataErasure
) {
}
