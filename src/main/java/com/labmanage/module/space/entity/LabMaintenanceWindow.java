package com.labmanage.module.space.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

/**
 * 场地维修时段表（lab_maintenance_window）。
 * <p>
 * AVAILABLE 场地的短期维修窗口；cancelled_at 为 NULL 表示生效中。
 * 重叠判定与 lab_slot 一致：半开区间 start_at < other.end AND end_at > other.start。
 */
@TableName("lab_maintenance_window")
public class LabMaintenanceWindow extends BaseEntity {

    /** 场地（→ lab.id） */
    private Long labId;

    /** 维修开始（UTC） */
    private Instant startAt;

    /** 维修结束（UTC） */
    private Instant endAt;

    /** 维修事由 */
    private String reason;

    /** 取消时间（UTC；NULL=生效中） */
    private Instant cancelledAt;

    public Long getLabId() {
        return labId;
    }

    public void setLabId(Long labId) {
        this.labId = labId;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
