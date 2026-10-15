package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.StockLocation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 库位 VO
 */
@Schema(description = "库位信息")
public record StockLocationVO(
        @Schema(description = "库位 ID", example = "1234567890123456789")
        String id,

        @Schema(description = "所属实验室 ID", example = "1234567890123456789")
        String labId,

        @Schema(description = "库位码", example = "LAB1-A-01")
        String locationCode,

        @Schema(description = "库位名称", example = "一号实验室 A 区药品柜")
        String locationName,

        @Schema(description = "位置详述", example = "A 区 3 层左柜")
        String locationDetail,

        @Schema(description = "启用：true/false")
        Boolean enabled,

        @Schema(description = "创建时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant createTime,

        @Schema(description = "更新时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant updateTime
) {

    public static StockLocationVO from(StockLocation e) {
        return new StockLocationVO(
                String.valueOf(e.getId()),
                e.getLabId() == null ? null : String.valueOf(e.getLabId()),
                e.getLocationCode(),
                e.getLocationName(),
                e.getLocationDetail(),
                e.getEnabled() != null && e.getEnabled() == 1,
                e.getCreateTime(),
                e.getUpdateTime()
        );
    }
}
