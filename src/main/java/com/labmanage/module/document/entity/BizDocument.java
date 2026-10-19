package com.labmanage.module.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

/**
 * 统一单据实体（所有审批型业务共用；EQUIPMENT_BOOKING 设备使用申请）
 */
@TableName("biz_document")
public class BizDocument extends BaseEntity {

    /** 单据编号（前缀+雪花，唯一） */
    private String documentNo;

    /** 单据类型（EQUIPMENT_BOOKING） */
    private String documentType;

    /** 归属用户（→ sys_user.id；申请人） */
    private Long ownerUserId;

    /** 归属部门（→ sys_department.id；申请人部门） */
    private Long departmentId;

    /** 审批策略（→ business_policy.id；一期 NULL） */
    private Long policyId;

    /** 提交时间（UTC） */
    private Instant submittedAt;

    /** 幂等键（客户端提供，缺省服务端生成 UUID） */
    private String idempotencyKey;

    public String getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(String documentNo) {
        this.documentNo = documentNo;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
