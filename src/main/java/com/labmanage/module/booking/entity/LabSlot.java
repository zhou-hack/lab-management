package com.labmanage.module.booking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

/**
 * 场地占用实体（lab_slot）。
 * <p>
 * 预约批准时写入一行（uk_lab_slot_booking 保证单预约单行）；
 * released_at IS NULL 的记录参与冲突判断，取消时写回释放时间与原因。
 */
@TableName("lab_slot")
public class LabSlot extends BaseEntity {

    /** 场地（→ biz_space.id） */
    private Long labId;

    /** 占用来源预约单（→ lab_booking.id；课程排课来源待课程模块落地） */
    private Long bookingId;

    /** 占用开始（UTC 时间点） */
    private Instant startAt;

    /** 占用结束（UTC 时间点） */
    private Instant endAt;

    /** 释放时间（NULL=未释放，未释放记录参与冲突） */
    private Instant releasedAt;

    /** 释放原因（MANUAL取消/FORCE强制结束） */
    private String releaseReason;

    public Long getLabId() {
        return labId;
    }

    public void setLabId(Long labId) {
        this.labId = labId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
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

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public void setReleaseReason(String releaseReason) {
        this.releaseReason = releaseReason;
    }
}
