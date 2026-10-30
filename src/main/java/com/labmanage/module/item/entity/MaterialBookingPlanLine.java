package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

@TableName("material_booking_plan_line")
public class MaterialBookingPlanLine extends BaseEntity {

    private Long planId;
    private Long materialId;
    private BigDecimal defaultQuantity;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public BigDecimal getDefaultQuantity() { return defaultQuantity; }
    public void setDefaultQuantity(BigDecimal defaultQuantity) { this.defaultQuantity = defaultQuantity; }
}
