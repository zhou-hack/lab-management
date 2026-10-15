package com.labmanage.module.booking.vo;

/**
 * 预约统计（预约管理页卡片：按落库状态四档 + 总数）。
 */
public record BookingStatsVO(
        Long total,
        Long pending,
        Long approved,
        Long rejected,
        Long cancelled
) {
}
