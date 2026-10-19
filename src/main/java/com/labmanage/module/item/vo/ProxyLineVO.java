package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialRequestLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 学生代理代领源申领行 VO（含剩余可代领量）
 * <p>
 * 剩余可代领量 = 源行批准量 − 源行实发量 − 已被有效代领单占用的申请量；
 * 有效代领单 = 未取消、未拒绝的 PROXY 单。
 */
@Schema(description = "代领源申领行")
public record ProxyLineVO(
        @Schema(description = "源申领行 ID（代领时作为 sourceLineId 提交）", example = "1234567890123456789")
        String lineId,

        @Schema(description = "物料 ID", example = "1234567890123456789")
        String skuId,

        @Schema(description = "SKU 码", example = "SKU-ETHANOL-500ML")
        String skuCode,

        @Schema(description = "物料名称", example = "无水乙醇 500mL")
        String materialName,

        @Schema(description = "计量单位", example = "瓶")
        String baseUnit,

        @Schema(description = "批准量（老师审核写入）", example = "10")
        BigDecimal approvedQty,

        @Schema(description = "已实发量", example = "2")
        BigDecimal fulfilledQty,

        @Schema(description = "已被代领单占用申请量", example = "3")
        BigDecimal occupiedQty,

        @Schema(description = "剩余可代领量（本代领单提交量的上限）", example = "5")
        BigDecimal availableQty
) {

    public static ProxyLineVO from(MaterialRequestLine line, String skuCode, String materialName,
                                   String baseUnit, BigDecimal occupiedQty) {
        BigDecimal approved = line.getApprovedQty() == null ? BigDecimal.ZERO : line.getApprovedQty();
        BigDecimal fulfilled = line.getFulfilledQty() == null ? BigDecimal.ZERO : line.getFulfilledQty();
        BigDecimal occupied = occupiedQty == null ? BigDecimal.ZERO : occupiedQty;
        return new ProxyLineVO(
                String.valueOf(line.getId()),
                String.valueOf(line.getSkuId()),
                skuCode,
                materialName,
                baseUnit,
                approved,
                fulfilled,
                occupied,
                approved.subtract(fulfilled).subtract(occupied).max(BigDecimal.ZERO)
        );
    }
}
