package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 库存调整 DTO（物料存量面板直接调整；后端生成 ADJUST 调账单并自动审批过账）
 */
@Schema(description = "库存调整请求（生成 ADJUST 调账单并自动过账，立即生效）")
public record StockBalanceAdjustDTO(
        @Schema(description = "调整后数量（非负；与当前库存一致时拒绝）", example = "25.5")
        @NotNull(message = "调整后数量不能为空")
        @DecimalMin(value = "0", message = "调整后数量不能为负")
        @Digits(integer = 18, fraction = 3, message = "调整后数量最多 3 位小数")
        BigDecimal targetQty,

        @Schema(description = "调整事由（审计必填）", example = "盘点差异补录")
        @NotBlank(message = "调整事由不能为空")
        @Size(max = 2000, message = "调整事由不能超过 2000 个字符")
        String reason
) {
}
