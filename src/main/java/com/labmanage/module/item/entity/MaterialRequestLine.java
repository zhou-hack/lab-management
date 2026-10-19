package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

/**
 * 物料申领行（申领单+SKU 唯一；实发量由出库过账回写）
 */
@TableName("material_request_line")
public class MaterialRequestLine extends BaseEntity {

    /** 所属申领单 ID */
    private Long requestId;

    /** 物料 SKU ID */
    private Long skuId;

    /** 申请量（> 0） */
    private BigDecimal requestedQty;

    /** 定额快照（缺定额记 0，须老师确认） */
    private BigDecimal quotaQtySnapshot;

    /** 超定额：1是 0否 */
    private Integer requiresExtraApproval;

    /** 双人签字：1是 0否（危险/高价值物料） */
    private Integer requiresDualSign;

    /** 源申领行 ID（学生代理代领时引用的老师已审核单行） */
    private Long sourceLineId;

    /** 批准量（审批时写入，不超过申请量） */
    private BigDecimal approvedQty;

    /** 实发量（过账回写，不超过批准量） */
    private BigDecimal fulfilledQty;

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public BigDecimal getRequestedQty() {
        return requestedQty;
    }

    public void setRequestedQty(BigDecimal requestedQty) {
        this.requestedQty = requestedQty;
    }

    public BigDecimal getQuotaQtySnapshot() {
        return quotaQtySnapshot;
    }

    public void setQuotaQtySnapshot(BigDecimal quotaQtySnapshot) {
        this.quotaQtySnapshot = quotaQtySnapshot;
    }

    public Integer getRequiresExtraApproval() {
        return requiresExtraApproval;
    }

    public void setRequiresExtraApproval(Integer requiresExtraApproval) {
        this.requiresExtraApproval = requiresExtraApproval;
    }

    public Integer getRequiresDualSign() {
        return requiresDualSign;
    }

    public void setRequiresDualSign(Integer requiresDualSign) {
        this.requiresDualSign = requiresDualSign;
    }

    public Long getSourceLineId() {
        return sourceLineId;
    }

    public void setSourceLineId(Long sourceLineId) {
        this.sourceLineId = sourceLineId;
    }

    public BigDecimal getApprovedQty() {
        return approvedQty;
    }

    public void setApprovedQty(BigDecimal approvedQty) {
        this.approvedQty = approvedQty;
    }

    public BigDecimal getFulfilledQty() {
        return fulfilledQty;
    }

    public void setFulfilledQty(BigDecimal fulfilledQty) {
        this.fulfilledQty = fulfilledQty;
    }
}
