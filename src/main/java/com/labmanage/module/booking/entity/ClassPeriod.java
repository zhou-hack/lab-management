package com.labmanage.module.booking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.LocalTime;

/**
 * 节次定义实体（class_period）。
 * <p>
 * 预约按「日期 + 节次」选择，start_at/end_at 由日期的上海本地节次时刻推导为
 * UTC 存贮；start_clock/end_clock 为上海本地日历时间（TIME(0)，无时区语义），
 * 不做 UTC 换算（与场地开放时段 open_start/open_end 同规则）。
 */
@TableName("class_period")
public class ClassPeriod extends BaseEntity {

    /** 节次号（全局唯一，升序） */
    private Integer periodNo;

    /** 显示名（如 第1节） */
    private String periodName;

    /** 开始时刻（上海本地时间） */
    private LocalTime startClock;

    /** 结束时刻（上海本地时间，须晚于 startClock） */
    private LocalTime endClock;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public Integer getPeriodNo() {
        return periodNo;
    }

    public void setPeriodNo(Integer periodNo) {
        this.periodNo = periodNo;
    }

    public String getPeriodName() {
        return periodName;
    }

    public void setPeriodName(String periodName) {
        this.periodName = periodName;
    }

    public LocalTime getStartClock() {
        return startClock;
    }

    public void setStartClock(LocalTime startClock) {
        this.startClock = startClock;
    }

    public LocalTime getEndClock() {
        return endClock;
    }

    public void setEndClock(LocalTime endClock) {
        this.endClock = endClock;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}