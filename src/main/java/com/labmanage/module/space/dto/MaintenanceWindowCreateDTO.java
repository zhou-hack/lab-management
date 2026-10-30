package com.labmanage.module.space.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 场地维修时段创建入参。
 * <p>
 * startAt/endAt 为带偏移 ISO 8601（由 Jackson 反序列化为 Instant，统一 UTC 毫秒）。
 */
public record MaintenanceWindowCreateDTO(

        @NotNull(message = "维修开始时间不能为空")
        Instant startAt,

        @NotNull(message = "维修结束时间不能为空")
        Instant endAt,

        @Size(max = 512, message = "维修事由不能超过 512 字")
        String reason
) {
}
