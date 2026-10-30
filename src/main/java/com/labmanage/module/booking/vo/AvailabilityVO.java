package com.labmanage.module.booking.vo;

import java.time.Instant;
import java.util.List;

/**
 * 场地某日可用时段（预约表单辅助）。
 * <p>
 * date 为上海本地日期（yyyy-MM-dd）；openStart/openEnd 为上海本地 HH:mm
 * （场地开放窗口，无时区换算）；occupied 为该日未释放占用（UTC 时间点，
 * 前端按上海时区展示，自由段 = 开放窗口减去占用段）；
 * maintenance 为当日生效中的维修时段（UTC 时间点，覆盖节次前端标灰不可选）。
 */
public record AvailabilityVO(
        String spaceId,
        String labName,
        Integer capacity,
        String openStart,
        String openEnd,
        String date,
        List<OccupiedSegment> occupied,
        List<OccupiedSegment> maintenance
) {

    /** 单个占用段（来自已批准预约） */
    public record OccupiedSegment(
            String bookingId,
            Instant startAt,
            Instant endAt
    ) {
    }
}
