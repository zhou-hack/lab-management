package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.MaterialType;

/**
 * 物料 SKU 主档（按"不可互换即分 SKU"建档）
 */
@TableName("material_sku")
public class MaterialSku extends BaseEntity {

    /** 所属分类 ID */
    private Long categoryId;

    /** SKU 码（唯一，逻辑删除后不复用） */
    private String skuCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String specification;

    /** 基础计量单位（单位固定） */
    private String baseUnit;

    /** 数量精度 0-3 */
    private Integer quantityScale;

    /** 物料类型 */
    private MaterialType materialType;

    /** 危险品：1是 0否 */
    private Integer isHazardous;

    /** 高价值：1是 0否 */
    private Integer isValuable;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

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

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public String getBaseUnit() {
        return baseUnit;
    }

    public void setBaseUnit(String baseUnit) {
        this.baseUnit = baseUnit;
    }

    public Integer getQuantityScale() {
        return quantityScale;
    }

    public void setQuantityScale(Integer quantityScale) {
        this.quantityScale = quantityScale;
    }

    public MaterialType getMaterialType() {
        return materialType;
    }

    public void setMaterialType(MaterialType materialType) {
        this.materialType = materialType;
    }

    public Integer getIsHazardous() {
        return isHazardous;
    }

    public void setIsHazardous(Integer isHazardous) {
        this.isHazardous = isHazardous;
    }

    public Integer getIsValuable() {
        return isValuable;
    }

    public void setIsValuable(Integer isValuable) {
        this.isValuable = isValuable;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
