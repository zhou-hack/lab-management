package com.labmanage.module.space.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 实验室开放规则创建请求（一期仅同日时间窗；时间为上海本地墙钟，日期为纯日期）
 */
public record OpenRuleCreateDTO(

        @NotNull(message = "星期不能为空")
        @Min(value = 1, message = "星期须为 1-7")
        @Max(value = 7, message = "星期须为 1-7")
        Integer weekday,

        @NotNull(message = "开放时间不能为空")
        LocalTime openTime,

        @NotNull(message = "关闭时间不能为空")
        LocalTime closeTime,

        @NotNull(message = "有效期开始不能为空")
        LocalDate validFrom,

        @NotNull(message = "有效期结束不能为空")
        LocalDate validTo
) {
}
