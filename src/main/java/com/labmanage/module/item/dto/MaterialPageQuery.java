package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.MaterialType;

public class MaterialPageQuery extends ItemPageQuery {

    private String materialName;
    private MaterialType materialType;
    private Integer enabled;

    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public MaterialType getMaterialType() { return materialType; }
    public void setMaterialType(MaterialType materialType) { this.materialType = materialType; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
