package com.labmanage.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 物料分类 VO
 */
@Schema(description = "物料分类信息")
public record MaterialCategoryVO(
        @Schema(description = "分类 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "分类编码", example = "CAT-CHEM")
        String categoryCode,

        @Schema(description = "分类名称", example = "化学试剂")
        String categoryName,

        @Schema(description = "父分类 ID（空为根节点）", example = "1234567890123456789")
        String parentId,

        @Schema(description = "启用：true/false")
        Boolean enabled,

        @Schema(description = "创建时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant createTime,

        @Schema(description = "更新时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant updateTime
) {

    public static MaterialCategoryVO from(com.labmanage.module.item.entity.MaterialCategory e) {
        return new MaterialCategoryVO(
                String.valueOf(e.getId()),
                e.getCategoryCode(),
                e.getCategoryName(),
                e.getParentId() == null ? null : String.valueOf(e.getParentId()),
                e.getEnabled() != null && e.getEnabled() == 1,
                e.getCreateTime(),
                e.getUpdateTime()
        );
    }
}
