package com.labmanage.module.space.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 实验室开放规则表（lab_open_rule）
 * openTime/closeTime 为上海本地墙钟时间（TIME(0)），validFrom/validTo 为纯日期，均不做时区转换。
 */
@TableName("lab_open_rule")
public class LabOpenRule extends BaseEntity {

    private Long labId;

    /** 星期：1-7（周一到周日） */
    private Integer weekday;

    private LocalTime openTime;

    private LocalTime closeTime;

    private LocalDate validFrom;

    private LocalDate validTo;

    private Integer enabled;

    public Long getLabId() {
        return labId;
    }

    public void setLabId(Long labId) {
        this.labId = labId;
    }

    public Integer getWeekday() {
        return weekday;
    }

    public void setWeekday(Integer weekday) {
        this.weekday = weekday;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public void setOpenTime(LocalTime openTime) {
        this.openTime = openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(LocalTime closeTime) {
        this.closeTime = closeTime;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
