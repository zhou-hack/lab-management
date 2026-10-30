package com.labmanage.module.item.dto;

public class StockLocationPageQuery extends ItemPageQuery {

    private String locationCode;
    private String locationName;
    private Integer enabled;

    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
