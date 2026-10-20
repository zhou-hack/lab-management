package com.labmanage.module.booking.vo;

import com.labmanage.module.booking.enums.SeriesStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 周期预约详情（审批页抽屉；附生成的各周预约单）。
 * 与 SeriesVO 同构，另附 overview 字段与 bookingIds 供前端展开核对。
 */
public record SeriesDetailVO(
        String id,
        String spaceId,
        String labName,
        String courseName,
        String className,
        String teacherId,
        String teacherName,
        String purpose,
        Integer participantCount,
        Integer weekday,
        String weekdayName,
        String startPeriodName,
        String endPeriodName,
        LocalDate cycleStartDate,
        LocalDate cycleEndDate,
        Integer weekCount,
        SeriesStatus status,
        String statusName,
        String rejectReason,
        String cancelReason,
        String cancelTypeName,
        Instant cancelledAt,
        String applicantId,
        String applicantName,
        Instant createTime,
        Instant updateTime,
        List<String> bookingIds,
        List<GeneratedBookingItem> generated
) {

    /** 生成单条目（供详情展开按周核对：日期 + 节次区间 + 状态） */
    public record GeneratedBookingItem(
            String bookingId,
            LocalDate bookingDate,
            String timeRange,
            String status,
            String statusName
    ) {
    }
}