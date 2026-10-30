package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

@TableName("material_settlement")
public class MaterialSettlement extends BaseEntity {

    private Long materialDocumentId;
    private Long sourceMaterialDocumentLineId;
    private Long materialId;
    private Long fixedAssetId;
    private String settlementType;
    private BigDecimal quantity;
    private String targetAssetStatus;
    private String reason;

    public Long getMaterialDocumentId() { return materialDocumentId; }
    public void setMaterialDocumentId(Long materialDocumentId) { this.materialDocumentId = materialDocumentId; }
    public Long getSourceMaterialDocumentLineId() { return sourceMaterialDocumentLineId; }
    public void setSourceMaterialDocumentLineId(Long sourceMaterialDocumentLineId) { this.sourceMaterialDocumentLineId = sourceMaterialDocumentLineId; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getFixedAssetId() { return fixedAssetId; }
    public void setFixedAssetId(Long fixedAssetId) { this.fixedAssetId = fixedAssetId; }
    public String getSettlementType() { return settlementType; }
    public void setSettlementType(String settlementType) { this.settlementType = settlementType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getTargetAssetStatus() { return targetAssetStatus; }
    public void setTargetAssetStatus(String targetAssetStatus) { this.targetAssetStatus = targetAssetStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
