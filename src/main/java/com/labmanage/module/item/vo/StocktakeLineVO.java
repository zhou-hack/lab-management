package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.StocktakeLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 盘点行 VO
 */
@Schema(description = "盘点行")
public record StocktakeLineVO(
        @Schema(description = "行 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "物料 ID", example = "1234567890123456789")
        String skuId,

        @Schema(description = "SKU 码", example = "SKU-ETHANOL-500ML")
        String skuCode,

        @Schema(description = "物料名称", example = "无水乙醇 500mL")
        String skuName,

        @Schema(description = "计量单位", example = "瓶")
        String baseUnit,

        @Schema(description = "账面量快照", example = "20")
        BigDecimal bookQtySnapshot,

        @Schema(description = "实盘量（未录入为空）", example = "18")
        BigDecimal countedQty,

        @Schema(description = "差异（实盘-账面，未录入为空）", example = "-2")
        BigDecimal differenceQty,

        @Schema(description = "差异原因（差异非零必填）", example = "一瓶破损未登记报废")
        String differenceReason,

        @Schema(description = "盘点人 ID", example = "1234567890123456789")
        String countedBy,

        @Schema(description = "实盘时间（UTC）", example = "2026-10-14T01:00:00.000Z")
        Instant countedAt
) {

    public static StocktakeLineVO from(StocktakeLine e, String skuCode, String skuName, String baseUnit) {
        BigDecimal difference = e.getCountedQty() == null ? null
                : e.getCountedQty().subtract(e.getBookQtySnapshot());
        return new StocktakeLineVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getSkuId()),
                skuCode,
                skuName,
                baseUnit,
                e.getBookQtySnapshot(),
                e.getCountedQty(),
                difference,
                e.getDifferenceReason(),
                e.getCountedBy() == null ? null : String.valueOf(e.getCountedBy()),
                e.getCountedAt()
        );
    }
}
