package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 库存单据详情 VO（含明细行）
 */
@Schema(description = "库存单据详情")
public record StockDocumentDetailVO(
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
        Instant createTime,

        @Schema(description = "明细行")
        List<StockDocumentLineVO> lines,

        @Schema(description = "合计金额（两位小数）", example = "255.00")
        BigDecimal totalAmount,

        @Schema(description = "经办人 ID（→ sys_user.id，单据实际操作人）", example = "1234567890123456789")
        String operatorId,

        @Schema(description = "经办人姓名（缺失用户时为空，前端回退显示 ID）", example = "张老师")
        String operatorName
) {

    public static StockDocumentDetailVO from(StockDocument e, List<StockDocumentLineVO> lines,
                                            BigDecimal totalAmount, Map<Long, String> userNames) {
        String operatorId = e.getOperatorId() == null ? null : String.valueOf(e.getOperatorId());
        return new StockDocumentDetailVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getDocumentId()),
                e.getMovementType(),
                e.getStatus(),
                e.getOwnerTeacherId() == null ? null : String.valueOf(e.getOwnerTeacherId()),
                e.getVendorName(),
                e.getReason(),
                e.getOccurredAt(),
                e.getPostedAt(),
                e.getCreateTime(),
                lines,
                totalAmount,
                operatorId,
                operatorId == null ? null : userNames.get(e.getOperatorId())
        );
    }
}
