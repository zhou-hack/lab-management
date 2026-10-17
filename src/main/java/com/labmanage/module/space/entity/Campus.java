package com.labmanage.module.space.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

/**
 * 校区表（campus）
 */
@TableName("campus")
public class Campus extends BaseEntity {

    private String campusCode;

    private String campusName;

    private String address;

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
