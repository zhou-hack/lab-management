package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 物料结算分页查询参数
 */
@Schema(description = "物料结算分页查询参数")
public class MaterialSettlementQuery extends BasePageQuery {

    @Schema(description = "原出库行 ID", example = "1234567890123456789")
    private String issueLineId;

    public String getIssueLineId() {
        return issueLineId;
    }

    public void setIssueLineId(String issueLineId) {
        this.issueLineId = issueLineId;
    }
}
