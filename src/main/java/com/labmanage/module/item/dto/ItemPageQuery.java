package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;

public class ItemPageQuery extends BasePageQuery {

    private String sortBy;
    private String sortOrder;

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
}
