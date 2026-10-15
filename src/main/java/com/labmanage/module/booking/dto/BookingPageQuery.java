package com.labmanage.module.booking.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.booking.enums.BookingStatus;

import java.time.Instant;

/**
 * 预约分页查询参数（「我的预约」与「全部预约」共用）。
 * startFrom/startTo 按预约开始时间过滤（带偏移量 ISO 8601）。
 */
public class BookingPageQuery extends BasePageQuery {

    /** 状态过滤 */
    private BookingStatus status;

    /** 场地过滤 */
    private Long spaceId;

    /** 预约开始时间下界（含） */
    private Instant startFrom;

    /** 预约开始时间上界（含） */
    private Instant startTo;

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public Instant getStartFrom() {
        return startFrom;
    }

    public void setStartFrom(Instant startFrom) {
        this.startFrom = startFrom;
    }

    public Instant getStartTo() {
        return startTo;
    }

    public void setStartTo(Instant startTo) {
        this.startTo = startTo;
    }
}
