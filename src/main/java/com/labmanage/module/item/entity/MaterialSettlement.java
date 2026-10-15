package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.SettlementType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 物料结算（归还登记与核销同事务；流水不可改写，纠错走业务流程）
 */
@TableName("material_settlement")
public class MaterialSettlement extends BaseEntity {

    /** 原出库行 ID（→ stock_document_line.id） */
    private Long issueLineId;

    /** 结算类型 */
    private SettlementType settlementType;

    /** 本次结算量（> 0） */
    private BigDecimal quantity;

    /** 经办人 ID（登录模块接入后由会话写入） */
    private Long operatorId;

    /** 结算时间（UTC） */
    private Instant settledAt;

    /** 对应归还行 ID（RETURNED 必填，同归还行唯一） */
    private Long returnLineId;

    /** 幂等单据 ID（单据中心建成前由本模块预生成占位） */
    private Long documentId;

    /** 差异/核销原因 */
    private String reason;

    public Long getIssueLineId() {
        return issueLineId;
    }

    public void setIssueLineId(Long issueLineId) {
        this.issueLineId = issueLineId;
    }

    public SettlementType getSettlementType() {
        return settlementType;
    }

    public void setSettlementType(SettlementType settlementType) {
        this.settlementType = settlementType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }

    public Long getReturnLineId() {
        return returnLineId;
    }

    public void setReturnLineId(Long returnLineId) {
        this.returnLineId = returnLineId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
