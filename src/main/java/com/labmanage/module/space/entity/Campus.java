package com.labmanage.module.space.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

/**
 * 校区实体（空间层级：campus → building → lab → equipment_asset）
 */
@TableName("campus")
public class Campus extends BaseEntity {

    /** 校区编码（唯一，删除后不复用） */
    private String campusCode;

    /** 校区名称 */
    private String campusName;

    /** 校区地址 */
    private String address;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public String getCampusCode() {
        return campusCode;
    }

    public void setCampusCode(String campusCode) {
        this.campusCode = campusCode;
    }

    public String getCampusName() {
        return campusName;
    }

    public void setCampusName(String campusName) {
        this.campusName = campusName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
