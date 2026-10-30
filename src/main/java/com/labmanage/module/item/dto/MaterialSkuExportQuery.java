package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * 物料导出查询参数（当前筛选范围导出全部匹配行）
 */
@Schema(description = "物料导出查询参数")
public class MaterialSkuExportQuery {

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

    @Schema(description = "导出格式：xlsx / csv", example = "xlsx")
    @Pattern(regexp = "xlsx|csv", message = "导出格式只能为 xlsx 或 csv")
    private String format;

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

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
