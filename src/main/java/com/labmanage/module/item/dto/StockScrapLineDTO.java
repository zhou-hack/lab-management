package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 报废单明细行 DTO
 */
@Schema(description = "报废单明细行")
public record StockScrapLineDTO(
        @Schema(description = "物料 ID", example = "1234567890123456789")
        @NotBlank(message = "物料不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "物料 ID 格式无效")
        String skuId,

        @Schema(description = "报废出库位 ID", example = "1234567890123456789")
        @NotBlank(message = "报废出库位不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "报废出库位 ID 格式无效")
        String fromLocationId,

        @Schema(description = "报废数量", example = "5")
        @NotNull(message = "报废数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "报废数量必须大于 0")
        @Digits(integer = 15, fraction = 3, message = "报废数量整数最多 15 位、小数最多 3 位")
        BigDecimal quantity
) {
}
