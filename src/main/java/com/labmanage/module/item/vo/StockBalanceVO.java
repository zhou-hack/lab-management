package com.labmanage.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 库存余额 VO（含预警状态：NORMAL 正常 / WARNING 预警 / OUT_OF_STOCK 缺货）
 */
@Schema(description = "库存余额信息")
public record StockBalanceVO(
        @Schema(description = "余额记录 ID", example = "1234567890123456789")
        String id,

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

        @Schema(description = "计量单位", example = "瓶")
        String baseUnit,

        @Schema(description = "当前库存量", example = "8")
        BigDecimal stockQty,

        @Schema(description = "安全库存（最低库存/预警阈值）", example = "10")
        BigDecimal safeQty,

        @Schema(description = "补货目标库存", example = "20")
        BigDecimal targetQty,

        @Schema(description = "建议补货量（max(0, 目标-可用)）", example = "12")
        BigDecimal suggestedQty,

        @Schema(description = "预警状态：NORMAL/WARNING/OUT_OF_STOCK", example = "WARNING")
        String warningLevel,

        @Schema(description = "乐观锁版本", example = "3")
        Integer version,

        @Schema(description = "更新时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant updateTime
) {
}
