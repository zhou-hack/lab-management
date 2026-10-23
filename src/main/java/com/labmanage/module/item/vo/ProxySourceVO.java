package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialRequest;
import com.labmanage.module.item.mapper.LabBookingRefMapper;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 学生代理代领源申领单 VO（老师端已审核通过的老师预约单）
 */
@Schema(description = "学生代理代领源申领单")
public record ProxySourceVO(
        @Schema(description = "源申领单 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "责任老师 ID", example = "1234567890123456789")
        String ownerTeacherId,

        @Schema(description = "责任老师姓名（解析失败为空，前端回退显示 ID）", example = "张老师")
        String ownerTeacherName,

        @Schema(description = "开课实例 ID", example = "1234567890123456789")
        String offeringId,

        @Schema(description = "课程名称（关联场地预约的课程名，未关联时为空）", example = "电力电子技术")
        String courseName,

        @Schema(description = "用途", example = "有机化学实验课耗材")
        String purpose,

        @Schema(description = "登记时间（UTC）", example = "2026-10-14T01:00:00.000Z")
        Instant createTime
) {

    public static ProxySourceVO from(MaterialRequest e, String ownerTeacherName,
                                     LabBookingRefMapper.BookingCourse course) {
        return new ProxySourceVO(
                String.valueOf(e.getId()),
                e.getOwnerTeacherId() == null ? null : String.valueOf(e.getOwnerTeacherId()),
                ownerTeacherName,
                e.getOfferingId() == null ? null : String.valueOf(e.getOfferingId()),
                course == null ? null : course.courseName(),
                e.getPurpose(),
                e.getCreateTime()
        );
    }
}
