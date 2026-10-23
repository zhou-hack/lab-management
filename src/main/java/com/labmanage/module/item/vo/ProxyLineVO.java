package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialRequestLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 学生代理代领源申领行 VO（含剩余可代领量）
 */
@Schema(description = "学生代理代领源申领行")
public record ProxyLineVO(
        @Schema(description = "源申领行 ID（代领提交时的 sourceLineId）", example = "1234567890123456789")
        String lineId,

        @Schema(description = "物料 ID", example = "1234567890123456789")
        String skuId,

        @Schema(description = "SKU 码", example = "SKU-ETHANOL-500ML")
        String skuCode,

        @Schema(description = "物料名称", example = "无水乙醇 500mL")
        String materialName,

        @Schema(description = "计量单位", example = "瓶")
        String baseUnit,

        @Schema(description = "源行批准量", example = "10")
        BigDecimal approvedQty,

        @Schema(description = "源行实发量", example = "0")
        BigDecimal fulfilledQty,

        @Schema(description = "已被有效代领单占用的申请量（未取消、未拒绝）", example = "2")
        BigDecimal occupiedQty,

        @Schema(description = "剩余可代领量 = 批准量 − 实发量 − 占用量", example = "8")
        BigDecimal availableQty
) {

    public static ProxyLineVO from(MaterialRequestLine line, String skuCode, String materialName,
                                   String baseUnit, BigDecimal occupiedQty) {
        BigDecimal approved = line.getApprovedQty() == null ? BigDecimal.ZERO : line.getApprovedQty();
        BigDecimal fulfilled = line.getFulfilledQty() == null ? BigDecimal.ZERO : line.getFulfilledQty();
        BigDecimal occupied = occupiedQty == null ? BigDecimal.ZERO : occupiedQty;
        BigDecimal available = approved.subtract(fulfilled).subtract(occupied).max(BigDecimal.ZERO);
        return new ProxyLineVO(
                String.valueOf(line.getId()),
                String.valueOf(line.getSkuId()),
                skuCode,
                materialName,
                baseUnit,
                line.getApprovedQty(),
                line.getFulfilledQty(),
                occupiedQty,
                available
        );
    }
}
