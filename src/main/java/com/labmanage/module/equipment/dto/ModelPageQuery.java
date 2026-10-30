package com.labmanage.module.equipment.dto;

import com.labmanage.common.core.BasePageQuery;

/**
 * 设备型号分页查询参数
 */
public class ModelPageQuery extends BasePageQuery {

    /** 型号码模糊匹配 */
    private String modelCode;

    /** 设备名称模糊匹配 */
    private String equipmentName;

    /** 分类过滤 */
    private Long categoryId;

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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
