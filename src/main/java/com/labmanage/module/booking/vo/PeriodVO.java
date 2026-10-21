package com.labmanage.module.booking.vo;

import java.time.LocalTime;

/**
 * 节次选项（时间轴节次卡片数据）。
 * id 为固定小 ID（与节次号一致），startClock/endClock 为上海本地 HH:mm。
 */
public record PeriodVO(
        String id,
        Integer periodNo,
        String periodName,
        String startClock,
        String endClock
) {
    public static String format(LocalTime time) {
        return time == null ? null : time.toString();
    }
}