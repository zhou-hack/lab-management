package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 库存单据 VO（列表项）
 */
@Schema(description = "库存单据信息")
public record StockDocumentVO(
        @Schema(description = "单据 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "统一单据 ID（单据中心接入前与单据 ID 一致）", example = "1234567890123456789")
        String documentId,

        @Schema(description = "业务类型", example = "PURCHASE_IN")
        MovementType movementType,

        @Schema(description = "单据状态", example = "POSTED")
        StockDocumentStatus status,

        @Schema(description = "归属老师 ID", example = "1234567890123456789")
        String ownerTeacherId,

        @Schema(description = "供应商名称", example = "国药集团试剂有限公司")
        String vendorName,

        @Schema(description = "事由", example = "10 月耗材补货")
        String reason,

        @Schema(description = "业务发生时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant occurredAt,

        @Schema(description = "过账时间（UTC，未过账为空）", example = "2026-10-13T01:05:00.000Z")
        Instant postedAt,

        @Schema(description = "登记时间（UTC）", example = "2026-10-13T00:58:00.000Z")
        Instant createTime
) {

    public static StockDocumentVO from(StockDocument e) {
        return new StockDocumentVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getDocumentId()),
                e.getMovementType(),
                e.getStatus(),
                e.getOwnerTeacherId() == null ? null : String.valueOf(e.getOwnerTeacherId()),
                e.getVendorName(),
                e.getReason(),
                e.getOccurredAt(),
                e.getPostedAt(),
                e.getCreateTime()
        );
    }
}
