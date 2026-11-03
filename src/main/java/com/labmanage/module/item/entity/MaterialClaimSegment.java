package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

@TableName("material_claim_segment")
public class MaterialClaimSegment extends BaseEntity {

    private Long claimId;
    private Integer revisionNo;
    private Integer segmentSeq;
    private Long startPeriodId;
    private Long endPeriodId;
    private Instant bookingStartAt;
    private Instant bookingEndAt;

    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public Integer getSegmentSeq() { return segmentSeq; }
    public void setSegmentSeq(Integer segmentSeq) { this.segmentSeq = segmentSeq; }
    public Long getStartPeriodId() { return startPeriodId; }
    public void setStartPeriodId(Long startPeriodId) { this.startPeriodId = startPeriodId; }
    public Long getEndPeriodId() { return endPeriodId; }
    public void setEndPeriodId(Long endPeriodId) { this.endPeriodId = endPeriodId; }
    public Instant getBookingStartAt() { return bookingStartAt; }
    public void setBookingStartAt(Instant bookingStartAt) { this.bookingStartAt = bookingStartAt; }
    public Instant getBookingEndAt() { return bookingEndAt; }
    public void setBookingEndAt(Instant bookingEndAt) { this.bookingEndAt = bookingEndAt; }
}
