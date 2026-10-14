package com.labmanage.module.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 场地预约申请入参。
 * <p>
 * startAt/endAt 必须为带偏移量的 ISO 8601 / RFC 3339（公共 InstantDeserializer
 * 解析，无偏移量/格式无效/小数秒超三位返回 40000）；服务端按上海时区校验
 * 同日、开放窗口与占用冲突。
 */
public record BookingCreateDTO(

        @NotNull(message = "场地不能为空")
        Long spaceId,

        @NotBlank(message = "使用事由不能为空")
        @Size(max = 512, message = "使用事由长度不能超过 512")
        String purpose,

        @NotNull(message = "参与人数不能为空")
        @Min(value = 1, message = "参与人数必须大于 0")
        Integer participantCount,

        @NotNull(message = "预约开始时间不能为空")
        Instant startAt,

        @NotNull(message = "预约结束时间不能为空")
        Instant endAt
) {
}
