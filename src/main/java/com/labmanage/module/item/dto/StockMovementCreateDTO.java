package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.MoveDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 新建库存流水 DTO（库存台账面板直接登记；后端生成 ADJUST 调账单并自动审批过账）
 */
@Schema(description = "新建库存流水请求（按变更后数量生成 ADJUST 调账单并自动过账，立即生效）")
public record StockMovementCreateDTO(
        @Schema(description = "库位 ID", example = "1234567890123456789")
        @NotBlank(message = "库位不能为空")
        String locationId,

        @Schema(description = "物料 ID", example = "1234567890123456789")
        @NotBlank(message = "物料不能为空")
        String skuId,

        @Schema(description = "方向：IN 入库 / OUT 出库（须与变更前后差值方向一致）", example = "IN")
        @NotNull(message = "方向不能为空")
        MoveDirection direction,

        @Schema(description = "变更后数量（非负；与当前库存一致时拒绝）", example = "25.5")
        @NotNull(message = "变更后数量不能为空")
        @DecimalMin(value = "0", message = "变更后数量不能为负")
        @Digits(integer = 18, fraction = 3, message = "变更后数量最多 3 位小数")
        BigDecimal targetQty,

        @Schema(description = "出入库时间（ISO 8601 带偏移量，如 2026-10-23T09:00:00+08:00）", example = "2026-10-23T09:00:00+08:00")
        @NotNull(message = "出入库时间不能为空")
        OffsetDateTime occurredAt,

        @Schema(description = "入库人/出库人姓名（登记留档，展示于流水的经办人）", example = "张三")
        @NotBlank(message = "入库人/出库人姓名不能为空")
        @Size(max = 64, message = "入库人/出库人姓名不能超过 64 个字符")
        String operatorName,

        @Schema(description = "事由（审计必填）", example = "补录 10 月入库")
        @NotBlank(message = "事由不能为空")
        @Size(max = 2000, message = "事由不能超过 2000 个字符")
        String reason
) {
}
