package com.labmanage.module.space.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

/**
 * 楼栋实体（同校区楼栋码唯一）
 */
@TableName("building")
public class Building extends BaseEntity {

    /** 所属校区（→ campus.id） */
    private Long campusId;

    /** 楼栋编码（同校区唯一，删除后不复用） */
    private String buildingCode;

    /** 楼栋名称 */
    private String buildingName;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public Long getCampusId() {
        return campusId;
    }

    public void setCampusId(Long campusId) {
        this.campusId = campusId;
    }

    public String getBuildingCode() {
        return buildingCode;
    }

    public void setBuildingCode(String buildingCode) {
        this.buildingCode = buildingCode;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public void setBuildingName(String buildingName) {
        this.buildingName = buildingName;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
