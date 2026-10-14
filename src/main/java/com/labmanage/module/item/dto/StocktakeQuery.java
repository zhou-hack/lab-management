package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.item.enums.StocktakeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 盘点单分页查询参数
 */
@Schema(description = "盘点单分页查询参数")
public class StocktakeQuery extends BasePageQuery {

    @Schema(description = "盘点库位 ID", example = "1234567890123456789")
    private String locationId;

    @Schema(description = "状态", example = "COUNTING")
    private StocktakeStatus status;

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public StocktakeStatus getStatus() {
        return status;
    }

    public void setStatus(StocktakeStatus status) {
        this.status = status;
    }
}
