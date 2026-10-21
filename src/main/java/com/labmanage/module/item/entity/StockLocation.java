package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

/**
 * 库位（一期库存归属具体实验室）
 */
@TableName("stock_location")
public class StockLocation extends BaseEntity {

    /** 所属实验室 ID（空间模块建成前允许 NULL） */
    private Long labId;

    /** 库位码（同实验室内唯一） */
    private String locationCode;

    /** 库位名称 */
    private String locationName;

    /** 位置详述 */
    private String locationDetail;

    /** 保管人 ID（→ sys_user.id，每库位一名；2026-10-19 需求定稿新增） */
    private Long custodianId;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public Long getLabId() {
        return labId;
    }

    public void setLabId(Long labId) {
        this.labId = labId;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getLocationDetail() {
        return locationDetail;
    }

    public void setLocationDetail(String locationDetail) {
        this.locationDetail = locationDetail;
    }

    public Long getCustodianId() {
        return custodianId;
    }

    public void setCustodianId(Long custodianId) {
        this.custodianId = custodianId;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
