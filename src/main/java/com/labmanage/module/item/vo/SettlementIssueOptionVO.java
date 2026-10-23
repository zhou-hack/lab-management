package com.labmanage.module.item.vo;

import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 可核销出库行选项 VO（核销登记下拉：对应库存流水中的出库记录）
 */
@Schema(description = "可核销出库行选项")
public record SettlementIssueOptionVO(
        @Schema(description = "原出库行 ID（提交核销用）", example = "1234567890123456789")
        String issueLineId,

        @Schema(description = "物料 SKU ID", example = "1234567890123456789")
        String skuId,

        @Schema(description = "物料编码", example = "PE-DIO-1N4007")
        String skuCode,

        @Schema(description = "物料名称", example = "整流二极管 1N4007")
        String materialName,

        @Schema(description = "基本单位", example = "个")
        String baseUnit,

        @Schema(description = "原出库量", example = "6")
        BigDecimal quantity,

        @Schema(description = "已结算量（含归还与核销）", example = "2")
        BigDecimal settledQty,

        @Schema(description = "剩余可核销量", example = "4")
        BigDecimal remainQty,

        @Schema(description = "出库时间（UTC）", example = "2026-10-23T01:00:00.000Z")
        Instant occurredAt,

        @Schema(description = "关联物料预约单 ID（直发无）", example = "1234567890123456789")
        String materialRequestId,

        @Schema(description = "课程名称（经物料预约关联场地预约解析，未关联为空）", example = "电力电子技术")
        String courseName
) {

    public static SettlementIssueOptionVO from(MaterialSettlementMapper.IssueOptionRow row) {
        return new SettlementIssueOptionVO(
                String.valueOf(row.issueLineId()),
                String.valueOf(row.skuId()),
                row.skuCode(),
                row.materialName(),
                row.baseUnit(),
                row.quantity(),
                row.settledQty(),
                row.remainQty(),
                row.occurredAt(),
                row.materialRequestId() == null ? null : String.valueOf(row.materialRequestId()),
                row.courseName()
        );
    }
}
