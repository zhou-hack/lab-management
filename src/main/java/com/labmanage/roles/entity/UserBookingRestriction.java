package com.labmanage.roles.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

/**
 * 预约限制表（user_booking_restriction）。
 * 时间内禁止指定 EQUIPMENT/LAB/ALL 预约；source_document_id 非空行为自动逾期冻结
 * （单据模块落地后接入），手工创建行 source_document_id 为 NULL 且允许多条。
 */
@TableName("user_booking_restriction")
public class UserBookingRestriction extends BaseEntity {

    /** 被限制用户（→ sys_user.id） */
    private Long userId;

    /** 来源单据（跨模块，手工创建为 NULL） */
    private Long sourceDocumentId;

    /** 限制类型：EQUIPMENT/LAB/ALL */
    private String restrictionType;

    private String reason;

    /** 限制开始（UTC） */
    private Instant startAt;

    /** 限制结束（UTC；NULL=直至撤销） */
    private Instant endAt;

    private Instant revokedAt;

    private Long revokedBy;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(Long sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }

    public String getRestrictionType() {
        return restrictionType;
    }

    public void setRestrictionType(String restrictionType) {
        this.restrictionType = restrictionType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Long getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(Long revokedBy) {
        this.revokedBy = revokedBy;
    }
}
