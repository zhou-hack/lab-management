package com.labmanage.module.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.document.enums.DocumentType;

import java.time.Instant;

/**
 * 统一单据实体（所有审批型业务共用）
 * <p>
 * 业务表 status 是当前业务状态的唯一来源，本表不另存 status；
 * 追加型历史数据，deleted 恒 0。
 */
@TableName("biz_document")
public class BizDocument extends BaseEntity {

    /** 单据编号（前缀+雪花，唯一） */
    private String documentNo;

    /** 单据类型 */
    private DocumentType documentType;

    /** 归属用户（→ sys_user.id） */
    private Long ownerUserId;

    /** 归属部门（→ sys_department.id） */
    private Long departmentId;

    /** 审批策略（→ business_policy.id，后续模块，暂不建外键） */
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

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
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
