package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

@TableName("material_booking_plan")
public class MaterialBookingPlan extends BaseEntity {

    private Long ownerTeacherId;
    private String planName;
    private Integer enabled;
    private String remark;

    public Long getOwnerTeacherId() { return ownerTeacherId; }
    public void setOwnerTeacherId(Long ownerTeacherId) { this.ownerTeacherId = ownerTeacherId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
