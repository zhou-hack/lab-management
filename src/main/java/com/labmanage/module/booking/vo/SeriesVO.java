package com.labmanage.module.booking.vo;

import com.labmanage.module.booking.enums.SeriesStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 周期预约列表行（「我的周期预约」「周期预约审批」共用）。
 * 周次 = 周期 [start,end] 内匹配星期的天数；seriesId 为雪花 ID 字符串承载。
 */
public record SeriesVO(
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
        Integer bookedCount,
        String applicantId,
        String applicantName,
        Instant createTime
) {
}