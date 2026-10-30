package com.labmanage.module.item.dto;

import jakarta.validation.constraints.DecimalMin;
import com.labmanage.module.item.validation.IntegerQuantity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ClaimPreviewAllocationDTO(
        @NotBlank(message = "分配物料不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "物料ID不合法") String materialId,
        @NotBlank(message = "分配库位不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "库位ID不合法") String locationId,
        @NotNull(message = "计划分配数量不能为空") @DecimalMin(value = "1", message = "计划分配数量必须大于0")
        @IntegerQuantity(message = "计划分配数量必须为整数，整数位数不能超过15位") BigDecimal plannedQuantity
) {
}
