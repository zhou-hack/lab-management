package com.labmanage.module.booking.flow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

/**
 * 统一单据实体（biz_document）。
 * <p>
 * 场地预约与 lab_booking 一一对应：document_type=LAB_BOOKING，
 * document_no="LAB"+预约ID，作为 approval_task/document_event 的归属根。
 */
@TableName("biz_document")
public class BizDocument extends BaseEntity {

    /** 单据编号（"LAB" + lab_booking.id，唯一） */
    private String documentNo;

    /** 单据类型：场地预约固定 LAB_BOOKING */
    private String documentType;

    /** 归属用户（= 预约申请人 → sys_user.id） */
    private Long ownerUserId;

    /** 归属部门（→ sys_department.id，取申请人部门，缺省设备处 1） */
    private Long departmentId;

    /** 审批策略（本期不用，可空） */
    private Long policyId;

    /** 提交时间（UTC） */
    private Instant submittedAt;

    /** 幂等键（预约场景取 lab_booking.id 字符串） */
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
