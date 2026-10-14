package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 库存预警分页查询参数（stock_qty ≤ safe_qty，0 单独标缺货）
 */
@Schema(description = "库存预警分页查询参数")
public class StockWarningQuery extends BasePageQuery {

    @Schema(description = "所属实验室 ID", example = "1234567890123456789")
    private String labId;

    @Schema(description = "库位 ID", example = "1234567890123456789")
    private String locationId;

    @Schema(description = "物料 ID", example = "1234567890123456789")
    private String skuId;

    @Schema(description = "仅看缺货（库存为 0）", example = "true")
    private Boolean shortageOnly;

    public String getLabId() {
        return labId;
    }

    public void setLabId(String labId) {
        this.labId = labId;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public Boolean getShortageOnly() {
        return shortageOnly;
    }

    public void setShortageOnly(Boolean shortageOnly) {
        this.shortageOnly = shortageOnly;
    }
}
