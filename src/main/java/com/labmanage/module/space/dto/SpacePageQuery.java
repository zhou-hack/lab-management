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
    private String spaceName;

    /** 位置（模糊） */
    private String location;

    /** 类型筛选 */
    private SpaceType spaceType;

    /** 状态筛选 */
    private SpaceStatus status;

    /** 最小容纳人数 */
    private Integer minCapacity;

    /** 最大容纳人数 */
    private Integer maxCapacity;

    /** 排序字段白名单：space_name/capacity/create_time/update_time */
    private String sortBy;

    /** 排序方向：asc/desc */
    private String sortOrder;

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public SpaceType getSpaceType() {
        return spaceType;
    }

    public void setSpaceType(SpaceType spaceType) {
        this.spaceType = spaceType;
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
