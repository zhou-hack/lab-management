package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.enums.SettlementType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 物料结算 VO
 */
@Schema(description = "物料结算记录")
public record MaterialSettlementVO(
        @Schema(description = "结算 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "原出库行 ID", example = "1234567890123456789")
        String issueLineId,

        @Schema(description = "结算类型", example = "CONSUMED")
        SettlementType settlementType,

        @Schema(description = "本次结算量", example = "5")
        BigDecimal quantity,

        @Schema(description = "经办人 ID", example = "1234567890123456789")
        String operatorId,

        @Schema(description = "结算时间（UTC）", example = "2026-10-14T01:00:00.000Z")
        Instant settledAt,

        @Schema(description = "对应归还行 ID（RETURNED 专有）", example = "1234567890123456789")
        String returnLineId,

        @Schema(description = "幂等单据 ID", example = "1234567890123456789")
        String documentId,

        @Schema(description = "差异/核销原因", example = "耗材实验中消耗完毕")
        String reason
) {

    public static MaterialSettlementVO from(MaterialSettlement e) {
        return new MaterialSettlementVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getIssueLineId()),
                e.getSettlementType(),
                e.getQuantity(),
                e.getOperatorId() == null ? null : String.valueOf(e.getOperatorId()),
                e.getSettledAt(),
                e.getReturnLineId() == null ? null : String.valueOf(e.getReturnLineId()),
                e.getDocumentId() == null ? null : String.valueOf(e.getDocumentId()),
                e.getReason()
        );
    }
}
