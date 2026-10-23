package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 可核销出库行选项分页查询参数（核销登记下拉选择）
 */
@Schema(description = "可核销出库行选项分页查询参数")
public class SettlementIssueOptionQuery extends BasePageQuery {

    @Schema(description = "物料编码/名称关键词", example = "乙醇")
    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
