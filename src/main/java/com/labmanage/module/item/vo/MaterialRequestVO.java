package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialRequest;
import com.labmanage.module.item.enums.MaterialRequestStatus;
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

        @Schema(description = "归属老师 ID", example = "1234567890123456789")
        String ownerTeacherId,

        @Schema(description = "经办人 ID", example = "1234567890123456789")
        String operatorId,

        @Schema(description = "开课实例 ID", example = "1234567890123456789")
        String offeringId,

        @Schema(description = "用途", example = "有机化学实验课耗材")
        String purpose,

        @Schema(description = "状态", example = "APPROVED")
        MaterialRequestStatus status,

        @Schema(description = "登记时间（UTC）", example = "2026-10-14T01:00:00.000Z")
        Instant createTime
) {

    public static MaterialRequestVO from(MaterialRequest e) {
        return new MaterialRequestVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getDocumentId()),
                e.getLabId() == null ? null : String.valueOf(e.getLabId()),
                e.getOwnerTeacherId() == null ? null : String.valueOf(e.getOwnerTeacherId()),
                e.getOperatorId() == null ? null : String.valueOf(e.getOperatorId()),
                e.getOfferingId() == null ? null : String.valueOf(e.getOfferingId()),
                e.getPurpose(),
                e.getStatus(),
                e.getCreateTime()
        );
    }
}
