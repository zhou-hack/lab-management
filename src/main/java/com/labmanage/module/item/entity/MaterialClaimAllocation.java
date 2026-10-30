package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

@TableName("material_claim_allocation")
public class MaterialClaimAllocation extends BaseEntity {

    private Long claimId;
    private Long claimLineId;
    private Integer revisionNo;
    private Long locationId;
    private BigDecimal plannedQuantity;

    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public Long getClaimLineId() { return claimLineId; }
    public void setClaimLineId(Long claimLineId) { this.claimLineId = claimLineId; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public BigDecimal getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(BigDecimal plannedQuantity) { this.plannedQuantity = plannedQuantity; }
}
