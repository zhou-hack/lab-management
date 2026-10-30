package com.labmanage.module.booking.vo;

import java.util.List;

/**
 * 全部场地某日占用总览项（场地预约时间轴「按日期筛选全部场地」三色卡片）。
 * <p>
 * openStart/openEnd 为上海本地 HH:mm；occupied 为该日未释放占用（UTC 时间点）；
 * maintenance 为当日生效中的维修时段（UTC 时间点；非空时卡片优先展示「有维修安排」）。
 * 三色由前端按节次派生：无占用=全天空闲；占用覆盖全部未过去节次=无空闲；其余=部分空闲。
 */
public record SpaceDayAvailabilityVO(
        String spaceId,
        String labName,
        Integer capacity,
        String openStart,
        String openEnd,
        List<AvailabilityVO.OccupiedSegment> occupied,
        List<AvailabilityVO.OccupiedSegment> maintenance
) {
}
