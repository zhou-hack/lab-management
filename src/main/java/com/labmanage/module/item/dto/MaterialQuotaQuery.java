package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 领用定额分页查询参数
 */
@Schema(description = "领用定额分页查询参数")
public class MaterialQuotaQuery extends BasePageQuery {

    @Schema(description = "开课实例 ID", example = "1234567890123456789")
    private String offeringId;

    @Schema(description = "物料 ID", example = "1234567890123456789")
    private String skuId;

    public String getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(String offeringId) {
        this.offeringId = offeringId;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }
}
