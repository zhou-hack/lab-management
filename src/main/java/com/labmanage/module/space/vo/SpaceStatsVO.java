package com.labmanage.module.space.vo;

/**
 * 场地统计视图（列表页 KPI 卡片）。
 * 占用为预约模块派生态，不在 biz_space 落库，故不参与统计。
 */
public record SpaceStatsVO(
        long total,
        long available,
        long maintaining,
        long closed
) {
}
