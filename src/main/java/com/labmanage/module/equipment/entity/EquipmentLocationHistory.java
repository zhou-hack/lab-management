package com.labmanage.module.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.time.Instant;

/**
 * 设备位置变迁历史实体（追加型，deleted 恒 0；当前台账与历史同事务更新）
 */
@TableName("equipment_location_history")
public class EquipmentLocationHistory extends BaseEntity {

    /** 设备（→ equipment_asset.id） */
    private Long assetId;

    /** 原位置说明（空间模块移除前为实验室维度迁移） */
    private String fromLocation;

    /** 新室内位置说明 */
    private String toLocation;

    /** 迁移时间（UTC） */
    private Instant movedAt;

    /** 经办人（→ sys_user.id） */
    private Long operatorId;

    /** 迁移原因 */
    private String reason;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(String fromLocation) {
        this.fromLocation = fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public void setToLocation(String toLocation) {
        this.toLocation = toLocation;
    }

    public Instant getMovedAt() {
        return movedAt;
    }

    public void setMovedAt(Instant movedAt) {
        this.movedAt = movedAt;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
