package com.labmanage.module.item.vo;

import com.labmanage.module.item.mapper.LabBookingRefMapper;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 场地预约单候选选项 VO（新建申领单选择关联预约）
 */
@Schema(description = "场地预约单候选选项")
public record BookingOptionVO(
        @Schema(description = "场地预约单 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "场地名称", example = "A703 网络实训室")
        String labName,

        @Schema(description = "课程名称", example = "网络综合布线")
        String courseName,

        @Schema(description = "班级", example = "计科 2401")
        String className,

        @Schema(description = "用途说明", example = "综合布线实训")
        String purpose,

        @Schema(description = "预约日期（纯日期）", example = "2026-10-21")
        LocalDate bookingDate,

        @Schema(description = "预约开始（UTC，ISO 8601）", example = "2026-10-21T00:00:00.000Z")
        Instant startAt,

        @Schema(description = "预约结束（UTC，ISO 8601）", example = "2026-10-21T01:35:00.000Z")
        Instant endAt,

        @Schema(description = "预约单状态", example = "PENDING")
        String status
) {

    public static BookingOptionVO from(LabBookingRefMapper.BookingOption o) {
        return new BookingOptionVO(
                String.valueOf(o.id()),
                o.labName(),
                o.courseName(),
                o.className(),
                o.purpose(),
                o.bookingDate(),
                o.startAt(),
                o.endAt(),
                o.status()
        );
    }
}
