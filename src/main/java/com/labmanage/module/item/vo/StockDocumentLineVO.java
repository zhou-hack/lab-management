package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.StockDocumentLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 库存单据明细行 VO
 */
@Schema(description = "库存单据明细行")
public record StockDocumentLineVO(
        @Schema(description = "行 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "行号", example = "1")
        Integer lineNo,

        @Schema(description = "物料 ID", example = "1234567890123456789")
        String skuId,

        @Schema(description = "SKU 码", example = "SKU-ETHANOL-500ML")
        String skuCode,

        @Schema(description = "物料名称", example = "无水乙醇 500mL")
        String skuName,

        @Schema(description = "计量单位", example = "瓶")
        String baseUnit,

        @Schema(description = "出库位 ID", example = "1234567890123456789")
        String fromLocationId,

        @Schema(description = "出库位名称", example = "一号实验室 A 区药品柜")
        String fromLocationName,

        @Schema(description = "入库位 ID", example = "1234567890123456789")
        String toLocationId,

        @Schema(description = "入库位名称", example = "一号实验室 A 区药品柜")
        String toLocationName,

        @Schema(description = "数量", example = "10")
        BigDecimal quantity
) {

    public static StockDocumentLineVO from(StockDocumentLine e, String skuCode, String skuName,
                                           String baseUnit, String fromLocationName, String toLocationName) {
        return new StockDocumentLineVO(
                String.valueOf(e.getId()),
                e.getLineNo(),
                String.valueOf(e.getSkuId()),
                skuCode,
                skuName,
                baseUnit,
                e.getFromLocationId() == null ? null : String.valueOf(e.getFromLocationId()),
                fromLocationName,
                e.getToLocationId() == null ? null : String.valueOf(e.getToLocationId()),
                toLocationName,
                e.getQuantity()
        );
    }
}
