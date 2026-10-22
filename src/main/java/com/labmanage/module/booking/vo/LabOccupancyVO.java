package com.labmanage.module.booking.vo;

import java.time.Instant;
import java.util.List;

/**
 * 场地占用总览（按日期 + 时段窗口筛选）。
 * <p>
 * 返回全部未删除场地：occupied=false 表示窗口内空闲可选中；
 * occupied=true 附首个占用段的老师/课程信息。slots 为窗口内全部
 * 未释放占用段（UTC 时间点，按开始时间升序），前端据此派生
 * 空闲/已占用/下节课预留/已过时释放四态展示。
 * status 非 AVAILABLE 的场地仅展示不参与占用判断。
 */
public record LabOccupancyVO(
        String spaceId,
        String labName,
        String labType,
        String labTypeName,
        String locationDetail,
        Integer capacity,
        String openStart,
        String openEnd,
        String status,
        String statusName,
        boolean occupied,
        String bookingId,
        String courseName,
        String teacherName,
        String applicantName,
        Instant occupyStartAt,
        Instant occupyEndAt,
        List<OccupiedSlot> slots
) {

    /** 窗口内单个未释放占用段（含占用单老师/课程，供四态派生与卡面展示） */
    public record OccupiedSlot(
            String bookingId,
            Instant startAt,
            Instant endAt,
            String courseName,
            String teacherName,
            String applicantName
    ) {
    }
}
