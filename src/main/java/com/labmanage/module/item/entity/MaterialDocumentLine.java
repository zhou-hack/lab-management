package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

@TableName("material_document_line")
public class MaterialDocumentLine extends BaseEntity {

    private Long materialDocumentId;
    private Long claimLineId;
    private Long sourceMaterialDocumentLineId;
    private Long materialId;
    private Long fixedAssetId;
    private BigDecimal quantity;
    private String positionText;

    public Long getMaterialDocumentId() { return materialDocumentId; }
    public void setMaterialDocumentId(Long materialDocumentId) { this.materialDocumentId = materialDocumentId; }
    public Long getClaimLineId() { return claimLineId; }
    public void setClaimLineId(Long claimLineId) { this.claimLineId = claimLineId; }
    public Long getSourceMaterialDocumentLineId() { return sourceMaterialDocumentLineId; }
    public void setSourceMaterialDocumentLineId(Long sourceMaterialDocumentLineId) { this.sourceMaterialDocumentLineId = sourceMaterialDocumentLineId; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getFixedAssetId() { return fixedAssetId; }
    public void setFixedAssetId(Long fixedAssetId) { this.fixedAssetId = fixedAssetId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getPositionText() { return positionText; }
    public void setPositionText(String positionText) { this.positionText = positionText; }
}
