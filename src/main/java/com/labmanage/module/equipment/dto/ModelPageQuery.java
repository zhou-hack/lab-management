package com.labmanage.module.equipment.dto;

import com.labmanage.common.core.BasePageQuery;

/**
 * 设备型号分页查询参数
 */
public class ModelPageQuery extends BasePageQuery {

    /** 关键字（型号码/名称/品牌模糊匹配） */
    private String keyword;

    /** 分类过滤 */
    private Long categoryId;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
