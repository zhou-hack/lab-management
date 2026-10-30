package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

@TableName("stock_balance")
public class StockBalance extends BaseEntity {

    private Long materialId;
    private Long locationId;
    private String positionText;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String positionKey;

    private BigDecimal ownedQuantity;
    private BigDecimal availableQuantity;
    private Integer version;

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getPositionText() { return positionText; }
    public void setPositionText(String positionText) { this.positionText = positionText; }
    public String getPositionKey() { return positionKey; }
    public void setPositionKey(String positionKey) { this.positionKey = positionKey; }
    public BigDecimal getOwnedQuantity() { return ownedQuantity; }
    public void setOwnedQuantity(BigDecimal ownedQuantity) { this.ownedQuantity = ownedQuantity; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal availableQuantity) { this.availableQuantity = availableQuantity; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
