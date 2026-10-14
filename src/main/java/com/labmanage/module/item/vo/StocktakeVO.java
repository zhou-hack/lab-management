package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.Stocktake;
import com.labmanage.module.item.enums.StocktakeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 盘点单 VO（分页行）
 */
@Schema(description = "盘点单")
public record StocktakeVO(
        @Schema(description = "盘点单 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "统一单据 ID", example = "1234567890123456789")
        String documentId,

        @Schema(description = "盘点库位 ID", example = "1234567890123456789")
        String locationId,

        @Schema(description = "盘点库位名称", example = "一号实验室 A 区药品柜")
        String locationName,

        @Schema(description = "学期 ID", example = "1234567890123456789")
        String semesterId,

        @Schema(description = "盘点日期", example = "2026-10-14")
        LocalDate countDate,

        @Schema(description = "状态", example = "COUNTING")
        StocktakeStatus status,

        @Schema(description = "开始时间（UTC）", example = "2026-10-14T01:00:00.000Z")
        Instant startedAt,

        @Schema(description = "完成时间（UTC）", example = "2026-10-14T03:00:00.000Z")
        Instant finishedAt,

        @Schema(description = "生成的调账单 ID", example = "1234567890123456789")
        String adjustDocumentId,

        @Schema(description = "登记时间（UTC）", example = "2026-10-14T00:58:00.000Z")
        Instant createTime
) {

    public static StocktakeVO from(Stocktake e, String locationName) {
        return new StocktakeVO(
                String.valueOf(e.getId()),
                String.valueOf(e.getDocumentId()),
                String.valueOf(e.getLocationId()),
                locationName,
                e.getSemesterId() == null ? null : String.valueOf(e.getSemesterId()),
                e.getCountDate(),
                e.getStatus(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getAdjustDocumentId() == null ? null : String.valueOf(e.getAdjustDocumentId()),
                e.getCreateTime()
        );
    }
}
