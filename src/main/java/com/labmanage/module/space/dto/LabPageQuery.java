package com.labmanage.module.space.dto;

import com.labmanage.common.core.BasePageQuery;

/**
 * 实验室分页查询参数
 */
public class LabPageQuery extends BasePageQuery {

    /** 关键字（编码/名称模糊匹配） */
    private String keyword;

    /** 校区过滤 */
    private Long campusId;

    /** 楼栋过滤 */
    private Long buildingId;

    /** 部门过滤 */
    private Long departmentId;

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

    public Long getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(Long buildingId) {
        this.buildingId = buildingId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }
}
