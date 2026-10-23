package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialQuota;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 领用定额 VO
 */
@Schema(description = "领用定额")
public record MaterialQuotaVO(
        @Schema(description = "定额 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "开课实例 ID", example = "1234567890123456789")
        String offeringId,

        @Schema(description = "物料 ID", example = "1234567890123456789")
        String skuId,

        @Schema(description = "SKU 码", example = "SKU-ETHANOL-500ML")
        String skuCode,

        @Schema(description = "物料名称", example = "无水乙醇 500mL")
        String skuName,

        @Schema(description = "规格型号", example = "AR 级，500mL/瓶")
        String specification,

        @Schema(description = "计量单位", example = "瓶")
        String baseUnit,

        @Schema(description = "分类 ID", example = "1234567890123456789")
        String categoryId,

        @Schema(description = "分类名称", example = "化学试剂")
        String categoryName,

        @Schema(description = "每人每课时定额", example = "0.05")
        BigDecimal qtyPerStudentPeriod,

        @Schema(description = "启用：1是 0否", example = "1")
        Integer enabled
) {

    public static MaterialQuotaVO from(MaterialQuota e, String skuCode, String skuName,
                                       String specification, String baseUnit,
                                       String categoryId, String categoryName) {
        return new MaterialQuotaVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getOfferingId()),
                String.valueOf(e.getSkuId()),
                skuCode,
                skuName,
                specification,
                baseUnit,
                categoryId,
                categoryName,
                e.getQtyPerStudentPeriod(),
                e.getEnabled()
        );
    }
}
