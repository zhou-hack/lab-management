package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.AssetStatus;

public class AssetPageQuery extends ItemPageQuery {

    private String assetCode;
    private String materialName;
    private String location;
    private Long locationId;
    private AssetStatus status;

    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }
}
