package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.enums.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 物料 SKU VO
 */
@Schema(description = "物料档案信息")
public record MaterialSkuVO(
        @Schema(description = "物料 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "所属分类 ID", example = "1234567890123456789")
        String categoryId,

        @Schema(description = "分类名称", example = "化学试剂")
        String categoryName,

        @Schema(description = "SKU 码", example = "SKU-ETHANOL-500ML")
        String skuCode,

        @Schema(description = "物料名称", example = "无水乙醇 500mL")
        String materialName,

        @Schema(description = "规格型号", example = "AR，500mL/瓶")
        String specification,

        @Schema(description = "基础计量单位", example = "瓶")
        String baseUnit,

        @Schema(description = "数量精度 0-3", example = "0")
        Integer quantityScale,

        @Schema(description = "物料类型：CONSUMABLE / RETURNABLE", example = "CONSUMABLE")
        MaterialType materialType,

        @Schema(description = "危险品", example = "false")
        Boolean isHazardous,

        @Schema(description = "高价值", example = "false")
        Boolean isValuable,

        @Schema(description = "启用：true/false")
        Boolean enabled,

        @Schema(description = "创建时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant createTime,

        @Schema(description = "更新时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant updateTime
) {

    public static MaterialSkuVO from(MaterialSku e, String categoryName) {
        return new MaterialSkuVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getCategoryId()),
                categoryName,
                e.getSkuCode(),
                e.getMaterialName(),
                e.getSpecification(),
                e.getBaseUnit(),
                e.getQuantityScale(),
                e.getMaterialType(),
                e.getIsHazardous() != null && e.getIsHazardous() == 1,
                e.getIsValuable() != null && e.getIsValuable() == 1,
                e.getEnabled() != null && e.getEnabled() == 1,
                e.getCreateTime(),
                e.getUpdateTime()
        );
    }
}
