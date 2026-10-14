package com.labmanage.module.space.dto;

import com.labmanage.common.core.BasePageQuery;

/**
 * 楼栋分页查询参数
 */
public class BuildingPageQuery extends BasePageQuery {

    /** 关键字（编码/名称模糊匹配） */
    private String keyword;

    /** 校区过滤 */
    private Long campusId;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCampusId() {
        return campusId;
    }

    public void setCampusId(Long campusId) {
        this.campusId = campusId;
    }
}
