package com.labmanage.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 库存台账（流水）VO
 */
@Schema(description = "库存台账流水信息")
public record StockMovementVO(
        @Schema(description = "流水 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "幂等键", example = "STK:1234567890123456789:1:IN")
        String movementKey,

        @Schema(description = "单据 ID", example = "1234567890123456789")
        String stockDocumentId,

        @Schema(description = "来源单据行 ID", example = "1234567890123456789")
        String documentLineId,

        @Schema(description = "库位 ID", example = "1234567890123456789")
        String locationId,

        @Schema(description = "库位名称", example = "一号实验室 A 区药品柜")
        String locationName,

        @Schema(description = "物料 ID", example = "1234567890123456789")
        String skuId,

        @Schema(description = "SKU 码", example = "SKU-ETHANOL-500ML")
        String skuCode,

        @Schema(description = "物料名称", example = "无水乙醇 500mL")
        String skuName,

        @Schema(description = "方向：IN/OUT", example = "IN")
        String direction,

        @Schema(description = "净变动（IN 正 / OUT 负）", example = "10")
        BigDecimal qtyDelta,

        @Schema(description = "变更前余额", example = "0")
        BigDecimal beforeQty,

        @Schema(description = "变更后余额", example = "10")
        BigDecimal afterQty,

        @Schema(description = "价值变动", example = "255.00")
        BigDecimal valueDelta,

        @Schema(description = "过账前均价", example = "0")
        BigDecimal beforeAvgPrice,

        @Schema(description = "过账后均价", example = "25.5000")
        BigDecimal afterAvgPrice,

        @Schema(description = "发生时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant occurredAt
) {
}
