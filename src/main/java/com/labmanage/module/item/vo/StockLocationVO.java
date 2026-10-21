package com.labmanage.module.item.vo;

import com.labmanage.module.item.entity.StockLocation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

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

        @Schema(description = "保管人 ID（→ sys_user.id，每库位一名）", example = "1234567890123456789")
        String custodianId,

        @Schema(description = "保管人姓名（缺失用户时为空，前端回退显示 ID）", example = "张老师")
        String custodianName,

        @Schema(description = "启用：true/false")
        Boolean enabled,

        @Schema(description = "创建时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant createTime,

        @Schema(description = "更新时间（UTC）", example = "2026-10-13T01:00:00.000Z")
        Instant updateTime
) {

    /**
     * names：用户 ID → 姓名（跨模块只读解析，调用方批量取好后传入；缺失返回空）
     */
    public static StockLocationVO from(StockLocation e, Map<Long, String> names) {
        return new StockLocationVO(
                String.valueOf(e.getId()),
                e.getLabId() == null ? null : String.valueOf(e.getLabId()),
                e.getLocationCode(),
                e.getLocationName(),
                e.getLocationDetail(),
                e.getCustodianId() == null ? null : String.valueOf(e.getCustodianId()),
                e.getCustodianId() == null ? null : names.get(e.getCustodianId()),
                e.getEnabled() != null && e.getEnabled() == 1,
                e.getCreateTime(),
                e.getUpdateTime()
        );
    }
}
