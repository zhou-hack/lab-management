package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.AssetStatus;

import java.math.BigDecimal;

@TableName("fixed_asset")
public class FixedAsset extends BaseEntity {

    private Long materialId;
    private String assetCode;
    private BigDecimal price;
    private Long locationId;
    private String positionText;
    private AssetStatus status;

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getPositionText() { return positionText; }
    public void setPositionText(String positionText) { this.positionText = positionText; }
    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }
}
