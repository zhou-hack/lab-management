package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StockLocationUpsertDTO(
        @NotBlank(message = "库位编码不能为空") @Size(max = 64, message = "库位编码长度不能超过64")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "库位编码仅支持字母、数字、点、下划线和横线") String locationCode,
        @NotBlank(message = "库位名称不能为空") @Size(max = 128, message = "库位名称长度不能超过128") String locationName,
        @Size(max = 512, message = "库位说明长度不能超过512") String description,
        @NotNull(message = "启用状态不能为空") @Min(value = 0, message = "启用状态仅支持0/1")
        @Max(value = 1, message = "启用状态仅支持0/1") Integer enabled
) {
}
