package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.enums.SettlementType;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 物料结算 VO（核销列表：关联出库流水补物料/库位/出库时间/课程详情）
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
        String reason,

        @Schema(description = "关联出库流水 ID（→ stock_movement.id，经原出库行一一对应）", example = "1234567890123456789")
        String movementId,

        @Schema(description = "物料编码（关联出库流水解析）", example = "PE-CAP-470UF50V")
        String skuCode,

        @Schema(description = "物料名称（关联出库流水解析）", example = "电解电容 470μF/50V")
        String materialName,

        @Schema(description = "基本单位", example = "个")
        String baseUnit,

        @Schema(description = "库位名称（出库流水发生库位）", example = "一号实验室 A 区元件柜")
        String locationName,

        @Schema(description = "出库时间（UTC，关联出库流水）", example = "2026-10-23T01:00:00.000Z")
        Instant occurredAt,

        @Schema(description = "课程名称（经物料预约关联场地预约解析，未关联为空）", example = "电力电子技术")
        String courseName
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
                e.getReason(),
                null, null, null, null, null, null, null
        );
    }

    /** 核销列表行（关联出库流水详情） */
    public static MaterialSettlementVO from(MaterialSettlementMapper.SettlementRow row) {
        return new MaterialSettlementVO(
                String.valueOf(row.id()),
                String.valueOf(row.issueLineId()),
                row.settlementType() == null ? null : SettlementType.valueOf(row.settlementType()),
                row.quantity(),
                row.operatorId() == null ? null : String.valueOf(row.operatorId()),
                row.settledAt(),
                row.returnLineId() == null ? null : String.valueOf(row.returnLineId()),
                row.documentId() == null ? null : String.valueOf(row.documentId()),
                row.reason(),
                row.movementId() == null ? null : String.valueOf(row.movementId()),
                row.skuCode(),
                row.materialName(),
                row.baseUnit(),
                row.locationName(),
                row.occurredAt(),
                row.courseName()
        );
    }
}
