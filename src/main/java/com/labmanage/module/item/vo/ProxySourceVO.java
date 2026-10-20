package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 学生代理代领源申领单 VO（老师已审核通过的本人申领单）
 */
@Schema(description = "代领源申领单")
public record ProxySourceVO(
        @Schema(description = "源申领单 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "责任老师 ID（源单归属老师，代领后绑定到代领单）", example = "1234567890123456789")
        String ownerTeacherId,

        @Schema(description = "责任老师姓名（解析失败为空）", example = "张老师")
        String ownerTeacherName,

        @Schema(description = "开课实例 ID", example = "1234567890123456789")
        String offeringId,

        @Schema(description = "用途", example = "有机化学实验课耗材")
        String purpose,

        @Schema(description = "登记时间（UTC）", example = "2026-10-14T01:00:00.000Z")
        Instant createTime
) {

    public static ProxySourceVO from(MaterialRequest e, String ownerTeacherName) {
        return new ProxySourceVO(
                String.valueOf(e.getId()),
                e.getOwnerTeacherId() == null ? null : String.valueOf(e.getOwnerTeacherId()),
                ownerTeacherName,
                e.getOfferingId() == null ? null : String.valueOf(e.getOfferingId()),
                e.getPurpose(),
                e.getCreateTime()
        );
    }
}
