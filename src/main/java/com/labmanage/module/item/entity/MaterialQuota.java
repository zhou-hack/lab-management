package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

/**
 * 领用定额（开课实例+SKU 唯一，驱动申领快照）
 */
@TableName("material_quota")
public class MaterialQuota extends BaseEntity {

    /** 开课实例 ID（跨模块引用） */
    private Long offeringId;

    /** 物料 SKU ID */
    private Long skuId;

    /** 每人每课时定额（非负） */
    private BigDecimal qtyPerStudentPeriod;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public Long getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(Long offeringId) {
        this.offeringId = offeringId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public BigDecimal getQtyPerStudentPeriod() {
        return qtyPerStudentPeriod;
    }

    public void setQtyPerStudentPeriod(BigDecimal qtyPerStudentPeriod) {
        this.qtyPerStudentPeriod = qtyPerStudentPeriod;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
