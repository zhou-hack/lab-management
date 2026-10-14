package com.labmanage.module.space.dto;

import com.labmanage.common.core.BasePageQuery;

/**
 * 校区分页查询参数
 */
public class CampusPageQuery extends BasePageQuery {

    /** 关键字（编码/名称模糊匹配） */
    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
