package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialRequestLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 物料申领行 VO
 */
@Schema(description = "物料申领行")
public record MaterialRequestLineVO(
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

        @Schema(description = "申请量", example = "10")
        BigDecimal requestedQty,

        @Schema(description = "定额快照", example = "6")
        BigDecimal quotaQtySnapshot,

        @Schema(description = "超定额：1是 0否", example = "1")
        Integer requiresExtraApproval,

        @Schema(description = "双人签字：1是 0否", example = "0")
        Integer requiresDualSign,

        @Schema(description = "批准量（未批准为空）", example = "8")
        BigDecimal approvedQty,

        @Schema(description = "实发量（未实发为 0）", example = "0")
        BigDecimal fulfilledQty
) {

    public static MaterialRequestLineVO from(MaterialRequestLine e, String skuCode, String skuName,
                                             String baseUnit) {
        return new MaterialRequestLineVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getSkuId()),
                skuCode,
                skuName,
                baseUnit,
                e.getRequestedQty(),
                e.getQuotaQtySnapshot(),
                e.getRequiresExtraApproval(),
                e.getRequiresDualSign(),
                e.getApprovedQty(),
                e.getFulfilledQty()
        );
    }
}
