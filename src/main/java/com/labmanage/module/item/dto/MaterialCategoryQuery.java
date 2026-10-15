package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 物料分类分页查询参数
 */
@Schema(description = "物料分类分页查询参数")
public class MaterialCategoryQuery extends BasePageQuery {

    @Schema(description = "关键词（匹配编码/名称）", example = "试剂")
    private String keyword;

    @Schema(description = "父分类 ID（空为全部）", example = "1234567890123456789")
    private String parentId;

    @Schema(description = "启用状态（空为全部）", example = "true")
    private Boolean enabled;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
