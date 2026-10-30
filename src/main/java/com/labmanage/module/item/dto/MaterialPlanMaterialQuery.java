package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.MaterialType;

public class MaterialPlanMaterialQuery extends ItemPageQuery {

    private MaterialType materialType;
    private String materialName;

    public MaterialPlanMaterialQuery() {
        setPageSize(10);
    }

    public MaterialType getMaterialType() { return materialType; }
    public void setMaterialType(MaterialType materialType) { this.materialType = materialType; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
}
