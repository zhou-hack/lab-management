package com.labmanage.module.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 场地预约申请入参。
 * <p>
 * startAt/endAt 必须为带偏移量的 ISO 8601 / RFC 3339（公共 InstantDeserializer
 * 解析，无偏移量/格式无效/小数秒超三位返回 40000）；服务端按上海时区校验
 * 同日、开放窗口与占用冲突。
 * <p>
 * 节次时间轴扩展（可选）：提交节次预约时携带 bookingDate + startPeriodId +
 * endPeriodId + courseName/className/teacherId。服务端将校验由节次时刻推导的
 * 时段与 startAt/endAt 一致（不一致返回 40000），保证时间轴口径一致；
 * 历史/纯时间点提交可不携带，各列在数据库为 NULL 兼容。
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
        Instant endAt,

        /** 预约日期（节次预约必填，与 startAt 当天上海日期一致） */
        LocalDate bookingDate,

        /** 起始节次（节次预约必填） */
        Long startPeriodId,

        /** 结束节次（节次预约必填） */
        Long endPeriodId,

        @Size(max = 128, message = "课程名称长度不能超过 128")
        String courseName,

        @Size(max = 128, message = "班级长度不能超过 128")
        String className,

        @Min(value = 1, message = "上课老师不合法")
        Long teacherId
) {
}