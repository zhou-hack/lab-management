package com.labmanage.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 申领单可归还行（已过账出库行 + 累计归还量 + 可归还量）
 */
@Schema(description = "申领单可归还行")
public record ReturnableLineVO(
        @Schema(description = "原出库行 ID（归还提交参数）", example = "1234567890123456789")
        String issueLineId,

        @Schema(description = "物料 ID", example = "1234567890123456789")
        String skuId,

        @Schema(description = "SKU 码", example = "SKU005")
        String skuCode,

        @Schema(description = "物料名称", example = "实验连接线")
        String materialName,

        @Schema(description = "基本单位", example = "根")
        String baseUnit,

        @Schema(description = "出库量（已过账）", example = "10")
        BigDecimal issuedQty,

        @Schema(description = "已归还量（已过账累计）", example = "4")
        BigDecimal returnedQty,

        @Schema(description = "可归还量 = 出库量 − 已归还量", example = "6")
        BigDecimal returnableQty,

        @Schema(description = "原出库位 ID（归还默认回原库位）", example = "1234567890123456789")
        String fromLocationId
) {
}
