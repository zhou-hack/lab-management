package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * 库存余额导出查询参数（当前筛选范围导出全部匹配行）
 */
@Schema(description = "库存余额导出查询参数")
public class StockBalanceExportQuery {

    @Schema(description = "所属实验室 ID（按库位归属过滤）", example = "1234567890123456789")
    private String labId;

    @Schema(description = "库位 ID", example = "1234567890123456789")
    private String locationId;

    @Schema(description = "物料 ID", example = "1234567890123456789")
    private String skuId;

    @Schema(description = "物料分类 ID（按物料归属分类过滤）", example = "1234567890123456789")
    private String categoryId;

    @Schema(description = "导出格式：xlsx / csv", example = "xlsx")
    @Pattern(regexp = "xlsx|csv", message = "导出格式只能为 xlsx 或 csv")
    private String format;

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

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
