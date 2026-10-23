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
        String courseName,

        @Schema(description = "关联出库流水 ID（→ stock_movement.id）", example = "1234567890123456789")
        String movementId,

        @Schema(description = "库位名称（出库流水发生库位）", example = "一号实验室 A 区元件柜")
        String locationName,

        @Schema(description = "出库人姓名（优先台账登记姓名，缺失按单据经办人解析）", example = "张老师")
        String operatorName,

        @Schema(description = "申领用途（关联物料预约单，直发为空）", example = "电力电子实验课耗材")
        String requestPurpose
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
                row.courseName(),
                row.movementId() == null ? null : String.valueOf(row.movementId()),
                row.locationName(),
                row.regOperatorName() != null ? row.regOperatorName() : row.operatorName(),
                row.requestPurpose()
        );
    }
}
