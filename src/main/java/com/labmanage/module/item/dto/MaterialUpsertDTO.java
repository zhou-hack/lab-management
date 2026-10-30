package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.QuantityMode;
import com.labmanage.module.item.enums.TrackMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MaterialUpsertDTO(
        @NotNull(message = "物料类型不能为空") MaterialType materialType,
        @NotNull(message = "跟踪方式不能为空") TrackMode trackMode,
        @Size(max = 64, message = "SKU编码长度不能超过64")
        @Pattern(regexp = "^[A-Za-z0-9_.-]*$", message = "SKU编码仅支持字母、数字、点、下划线和横线") String skuCode,
        @NotBlank(message = "物料名称不能为空") @Size(max = 128, message = "物料名称长度不能超过128") String materialName,
        @Size(max = 256, message = "规格型号长度不能超过256") String specification,
        @NotBlank(message = "基本单位不能为空") @Size(max = 32, message = "基本单位长度不能超过32") String baseUnit,
        @NotNull(message = "数量模式不能为空") QuantityMode quantityMode,
        @NotNull(message = "启用状态不能为空") @Min(value = 0, message = "启用状态仅支持0/1")
        @Max(value = 1, message = "启用状态仅支持0/1") Integer enabled
) {
}
