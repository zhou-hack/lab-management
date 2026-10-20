package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

/**
 * 物料分类导出查询参数
 */
@Schema(description = "物料分类导出查询参数")
public class MaterialCategoryExportQuery {

    @Schema(description = "关键词（匹配分类编码/名称）", example = "化学")
    private String keyword;

    @Schema(description = "父分类 ID", example = "1234567890123456789")
    private String parentId;

    @Schema(description = "启用状态（空为全部）", example = "true")
    private Boolean enabled;

    @Schema(description = "导出格式：xlsx / csv", example = "xlsx")
    @Pattern(regexp = "xlsx|csv", message = "导出格式只能为 xlsx 或 csv")
    private String format;

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

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
