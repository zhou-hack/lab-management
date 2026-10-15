package com.labmanage.module.space.vo;

import java.util.List;

/**
 * 场地统计视图（列表页 KPI 卡片）。
 * 占用为预约模块派生态，不在 lab 落库，故不参与统计；
 * typeDistribution 为按类型分布（导出/看板辅助）。
 */
public record SpaceStatsVO(
        long total,
        long available,
        long maintaining,
        long closed,
        List<TypeCountVO> typeDistribution
) {

    /** 单个类型计数 */
    public record TypeCountVO(
            String labType,
            String labTypeName,
            long count
    ) {
    }
}
