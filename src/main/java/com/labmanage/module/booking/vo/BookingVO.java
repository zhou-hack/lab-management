package com.labmanage.module.booking.vo;

import com.labmanage.module.booking.enums.BookingStatus;

import java.time.Instant;

/**
 * 预约列表行（「我的预约」与「全部预约」共用）。
 * <p>
 * runtimePhase 为派生展示字段（仅 APPROVED 有值）：UPCOMING 未开始 /
 * IN_USE 使用中 / FINISHED 已结束（按当前时间与预约起止比较，不落库）。
 * checkinAt/checkoutAt 来自 document_event 的开门签到/结束清点记录。
 * <p>
 * 节次时间轴扩展：courseName/className/teacherName 回填课程/班级/上课老师；
 * bookingDate/startPeriodName/endPeriodName 供时间轴卡片展示；rejectReason
 * 供列表直接回显驳回原因（学生只读视角无需进详情即可看见）。
 */
public record BookingVO(
        String id,
        String spaceId,
        String labName,
        String courseName,
        String className,
        String teacherId,
        String teacherName,
        String purpose,
        Integer participantCount,
        Instant startAt,
        Instant endAt,
        java.time.LocalDate bookingDate,
        String startPeriodName,
        String endPeriodName,
        BookingStatus status,
        String statusName,
        String runtimePhase,
        String applicantId,
        String applicantName,
        String rejectReason,
        Instant createTime,
        Instant checkinAt,
        Instant checkoutAt
) {
}