package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

@TableName("material_claim_line")
public class MaterialClaimLine extends BaseEntity {

    private Long claimId;
    private Integer revisionNo;
    private Long materialId;
    private BigDecimal requestedQuantity;
    private BigDecimal issuedQuantity;

    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public void setRequestedQuantity(BigDecimal requestedQuantity) { this.requestedQuantity = requestedQuantity; }
    public BigDecimal getIssuedQuantity() { return issuedQuantity; }
    public void setIssuedQuantity(BigDecimal issuedQuantity) { this.issuedQuantity = issuedQuantity; }
}
