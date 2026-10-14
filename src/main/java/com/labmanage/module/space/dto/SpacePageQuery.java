package com.labmanage.module.space.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.enums.SpaceType;

/**
 * 场地分页查询条件。
 * 多条件 AND 组合；排序字段走白名单（见 SpaceService 实现），非法值回退默认。
 */
public class SpacePageQuery extends BasePageQuery {

    /** 场地名称（模糊） */
    private String labName;

    /** 位置（模糊） */
    private String locationDetail;

    /** 类型筛选 */
    private SpaceType labType;

    /** 状态筛选 */
    private SpaceStatus status;

    /** 最小容纳人数 */
    private Integer minCapacity;

    /** 最大容纳人数 */
    private Integer maxCapacity;

    /** 排序字段白名单：lab_name/capacity/create_time/update_time */
    private String sortBy;

    /** 排序方向：asc/desc */
    private String sortOrder;

    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }

    public String getLocationDetail() {
        return locationDetail;
    }

    public void setLocationDetail(String locationDetail) {
        this.locationDetail = locationDetail;
    }

    public SpaceType getLabType() {
        return labType;
    }

    public void setLabType(SpaceType labType) {
        this.labType = labType;
    }

    public SpaceStatus getStatus() {
        return status;
    }

    public void setStatus(SpaceStatus status) {
        this.status = status;
    }

    public Integer getMinCapacity() {
        return minCapacity;
    }

    public void setMinCapacity(Integer minCapacity) {
        this.minCapacity = minCapacity;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
