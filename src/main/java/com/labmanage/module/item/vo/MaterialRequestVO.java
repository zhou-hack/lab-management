package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialRequest;
import com.labmanage.module.item.enums.MaterialRequestStatus;
import com.labmanage.module.item.enums.RequestType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 物料申领单 VO（分页行）
 */
@Schema(description = "物料申领单")
public record MaterialRequestVO(
        @Schema(description = "申领单 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "统一单据 ID", example = "1234567890123456789")
        String documentId,

        @Schema(description = "领用实验室 ID", example = "1234567890123456789")
        String labId,

        @Schema(description = "责任老师 ID（本人申领为归属老师；代领为源单归属老师）", example = "1234567890123456789")
        String ownerTeacherId,

        @Schema(description = "责任老师姓名（解析失败为空，前端回退显示 ID）", example = "张老师")
        String ownerTeacherName,

        @Schema(description = "代领人 ID（学生代理代领时为代理学生本人，经办人）", example = "1234567890123456789")
        String operatorId,

        @Schema(description = "代领人姓名（解析失败为空，前端回退显示 ID）", example = "李同学")
        String operatorName,

        @Schema(description = "开课实例 ID", example = "1234567890123456789")
        String offeringId,

        @Schema(description = "用途", example = "有机化学实验课耗材")
        String purpose,

        @Schema(description = "申领类型：SELF 本人申领 / PROXY 学生代理代领", example = "SELF")
        RequestType requestType,

        @Schema(description = "源申领单 ID（PROXY 时非空）", example = "1234567890123456789")
        String sourceRequestId,

        @Schema(description = "状态", example = "APPROVED")
        MaterialRequestStatus status,

        @Schema(description = "登记时间（UTC）", example = "2026-10-14T01:00:00.000Z")
        Instant createTime
) {

    public static MaterialRequestVO from(MaterialRequest e, String ownerTeacherName, String operatorName) {
        return new MaterialRequestVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getDocumentId()),
                e.getLabId() == null ? null : String.valueOf(e.getLabId()),
                e.getOwnerTeacherId() == null ? null : String.valueOf(e.getOwnerTeacherId()),
                ownerTeacherName,
                e.getOperatorId() == null ? null : String.valueOf(e.getOperatorId()),
                operatorName,
                e.getOfferingId() == null ? null : String.valueOf(e.getOfferingId()),
                e.getPurpose(),
                e.getRequestType(),
                e.getSourceRequestId() == null ? null : String.valueOf(e.getSourceRequestId()),
                e.getStatus(),
                e.getCreateTime()
        );
    }
}
