package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 库位分页查询参数
 */
@Schema(description = "库位分页查询参数")
public class StockLocationQuery extends BasePageQuery {

    @Schema(description = "所属实验室 ID", example = "1234567890123456789")
    private String labId;

    @Schema(description = "关键词（匹配库位码/名称）", example = "药品柜")
    private String keyword;

    @Schema(description = "启用状态（空为全部）", example = "true")
    private Boolean enabled;

    public String getLabId() {
        return labId;
    }

    public void setLabId(String labId) {
        this.labId = labId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
