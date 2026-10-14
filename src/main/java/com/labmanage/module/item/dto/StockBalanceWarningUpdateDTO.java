package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 库存预警配置更新 DTO（安全库存与补货目标库存）
 */
@Schema(description = "库存预警配置更新请求")
public record StockBalanceWarningUpdateDTO(
        @Schema(description = "安全库存（预警阈值，非负）", example = "10.5")
        @NotNull(message = "安全库存不能为空")
        @DecimalMin(value = "0", message = "安全库存不能为负")
        @Digits(integer = 18, fraction = 3, message = "安全库存最多 3 位小数")
        BigDecimal safeQty,

        @Schema(description = "补货目标库存（≥ 安全库存）", example = "20")
        @NotNull(message = "补货目标库存不能为空")
        @DecimalMin(value = "0", message = "补货目标库存不能为负")
        @Digits(integer = 18, fraction = 3, message = "补货目标库存最多 3 位小数")
        BigDecimal targetQty
) {
}
