package com.labmanage.module.space.vo;

import java.time.Instant;

/**
 * 场地维修时段视图（UTC 时间点，前端按上海时区展示）。
 *
 * @param id     维修时段 ID（字符串，避免 JS 精度损失）
 * @param labId  场地 ID
 * @param startAt 维修开始（UTC）
 * @param endAt   维修结束（UTC）
 * @param reason  维修事由
 */
public record MaintenanceWindowVO(
        String id,
        String labId,
        Instant startAt,
        Instant endAt,
        String reason
) {
}
