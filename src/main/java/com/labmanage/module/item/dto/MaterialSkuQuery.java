package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.item.enums.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 物料 SKU 分页查询参数
 */
@Schema(description = "物料分页查询参数")
public class MaterialSkuQuery extends BasePageQuery {

    @Schema(description = "SKU 码（模糊匹配）", example = "SKU001")
    private String skuCode;

    @Schema(description = "物料名称（模糊匹配）", example = "单股铜芯线")
    private String materialName;

    @Schema(description = "所属分类 ID", example = "1234567890123456789")
    private String categoryId;

    @Schema(description = "物料类型：CONSUMABLE / RETURNABLE", example = "CONSUMABLE")
    private MaterialType materialType;

    @Schema(description = "启用状态（空为全部）", example = "true")
    private Boolean enabled;

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
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
