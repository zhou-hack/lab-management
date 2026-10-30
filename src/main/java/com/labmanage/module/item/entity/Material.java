package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.QuantityMode;
import com.labmanage.module.item.enums.TrackMode;

@TableName("material")
public class Material extends BaseEntity {

    private MaterialType materialType;
    private TrackMode trackMode;
    private String skuCode;
    private String materialName;
    private String specification;
    private String baseUnit;
    private QuantityMode quantityMode;
    private Integer enabled;

    public MaterialType getMaterialType() { return materialType; }
    public void setMaterialType(MaterialType materialType) { this.materialType = materialType; }
    public TrackMode getTrackMode() { return trackMode; }
    public void setTrackMode(TrackMode trackMode) { this.trackMode = trackMode; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public String getBaseUnit() { return baseUnit; }
    public void setBaseUnit(String baseUnit) { this.baseUnit = baseUnit; }
    public QuantityMode getQuantityMode() { return quantityMode; }
    public void setQuantityMode(QuantityMode quantityMode) { this.quantityMode = quantityMode; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
