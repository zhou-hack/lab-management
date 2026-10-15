package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 库存余额分页查询参数
 */
@Schema(description = "库存余额分页查询参数")
public class StockBalanceQuery extends BasePageQuery {

    @Schema(description = "所属实验室 ID（按库位归属过滤）", example = "1234567890123456789")
    private String labId;

    @Schema(description = "库位 ID", example = "1234567890123456789")
    private String locationId;

    @Schema(description = "物料 ID", example = "1234567890123456789")
    private String skuId;

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
}
