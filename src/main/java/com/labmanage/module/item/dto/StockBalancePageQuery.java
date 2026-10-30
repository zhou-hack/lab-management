package com.labmanage.module.item.dto;

public class StockBalancePageQuery extends ItemPageQuery {

    private String materialName;
    private Long locationId;

    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
}
