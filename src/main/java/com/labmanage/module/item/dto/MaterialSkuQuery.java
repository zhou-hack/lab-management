package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.item.enums.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 物料 SKU 分页查询参数
 */
@Schema(description = "物料分页查询参数")
public class MaterialSkuQuery extends BasePageQuery {

    @Schema(description = "关键词（匹配 SKU 码/名称/规格）", example = "乙醇")
    private String keyword;

    @Schema(description = "所属分类 ID", example = "1234567890123456789")
    private String categoryId;

    @Schema(description = "物料类型：CONSUMABLE / RETURNABLE", example = "CONSUMABLE")
    private MaterialType materialType;

    @Schema(description = "启用状态（空为全部）", example = "true")
    private Boolean enabled;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public MaterialType getMaterialType() {
        return materialType;
    }

    public void setMaterialType(MaterialType materialType) {
        this.materialType = materialType;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
