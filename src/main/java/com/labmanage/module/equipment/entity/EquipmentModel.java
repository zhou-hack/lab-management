package com.labmanage.module.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

/**
 * 设备型号实体（型号码唯一；参考单价与每台实际采购价分开）
 */
@TableName("equipment_model")
public class EquipmentModel extends BaseEntity {

    /** 所属分类（→ equipment_category.id） */
    private Long categoryId;

    /** 型号码（唯一，删除后不复用） */
    private String modelCode;

    /** 设备名称 */
    private String equipmentName;

    /** 品牌 */
    private String brand;

    /** 规格说明 */
    private String specification;

    /** 数量（登记设备台数，配合计量单位阅读） */
    private Integer quantity;

    /** 计量单位（台/套等；列名 base_unit，接口契约沿用 unitName） */
    @TableField("base_unit")
    private String unitName;

    /** 参考单价（实际采购价见 equipment_asset.purchase_price） */
    private BigDecimal referencePrice;

    /** 预计寿命（月） */
    private Integer expectedLifeMonths;

    /** 归还须数据擦除：0否 1是 */
    private Integer requiresDataErasure;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public BigDecimal getReferencePrice() {
        return referencePrice;
    }

    public void setReferencePrice(BigDecimal referencePrice) {
        this.referencePrice = referencePrice;
    }

    public Integer getExpectedLifeMonths() {
        return expectedLifeMonths;
    }

    public void setExpectedLifeMonths(Integer expectedLifeMonths) {
        this.expectedLifeMonths = expectedLifeMonths;
    }

    public Integer getRequiresDataErasure() {
        return requiresDataErasure;
    }

    public void setRequiresDataErasure(Integer requiresDataErasure) {
        this.requiresDataErasure = requiresDataErasure;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
