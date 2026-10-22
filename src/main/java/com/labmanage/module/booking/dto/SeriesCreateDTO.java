package com.labmanage.module.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 批量周期预约申请入参。
 * <p>
 * 老师一次提交「场地 + 固定星期 + 起始/结束节次 + 使用周期（起止日期）」；
 * 周期内每个匹配星期（cycle [start,end] 内与 weekday 相同的日期）在批准后
 * 生成一张预约单并占用对应节次时段。idempotencyKey 防重复提交。
 */
public record SeriesCreateDTO(

        @NotNull(message = "场地不能为空")
        Long spaceId,

        @Size(max = 128, message = "课程名称长度不能超过 128")
        String courseName,

        @Size(max = 128, message = "班级长度不能超过 128")
        String className,

        @Min(value = 1, message = "上课老师不合法")
        Long teacherId,

        @NotBlank(message = "使用事由不能为空")
        @Size(max = 512, message = "使用事由长度不能超过 512")
        String purpose,

        @NotNull(message = "参与人数不能为空")
        @Min(value = 1, message = "参与人数必须大于 0")
        Integer participantCount,

        @NotNull(message = "必须指定星期")
        @Min(value = 1, message = "星期取值 1-7（1=周一）")
        @Max(value = 7, message = "星期取值 1-7（1=周一）")
        Integer weekday,

        @NotNull(message = "必须指定起始节次")
        Long startPeriodId,

        @NotNull(message = "必须指定结束节次")
        Long endPeriodId,

        @NotNull(message = "必须指定周期开始日期")
        LocalDate cycleStartDate,

        @NotNull(message = "必须指定周期结束日期")
        LocalDate cycleEndDate,

        @NotBlank(message = "幂等键不能为空")
        @Size(max = 128, message = "幂等键长度不能超过 128")
        String idempotencyKey
) {
}