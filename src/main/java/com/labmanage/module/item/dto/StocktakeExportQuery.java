package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.StocktakeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * 盘点单导出查询参数（当前筛选范围导出全部匹配行，不分页）
 */
@Schema(description = "盘点单导出查询参数")
public class StocktakeExportQuery {

    @Schema(description = "盘点库位 ID", example = "1234567890123456789")
    private String locationId;

    @Schema(description = "状态", example = "COUNTING")
    private StocktakeStatus status;

    @Schema(description = "导出格式：xlsx / csv", example = "xlsx")
    @Pattern(regexp = "xlsx|csv", message = "导出格式只能为 xlsx 或 csv")
    private String format;

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

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
