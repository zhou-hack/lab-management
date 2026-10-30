package com.labmanage.module.equipment.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.equipment.enums.EquipmentStatus;

/**
 * 设备台账分页查询参数
 * <p>
 * 无台账维护权限的请求由服务端强制收敛为「在架」目录视图
 * （status = IDLE 且启用）。
 */
public class AssetPageQuery extends BasePageQuery {

    /** 资产编号模糊匹配 */
    private String assetCode;

    /** 设备名称模糊匹配（按所属型号名称） */
    private String equipmentName;

    /** 归口部门过滤 */
    private Long departmentId;

    /** 型号过滤 */
    private Long modelId;

    /** 状态过滤（仅维护权限者生效） */
    private EquipmentStatus status;

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public EquipmentStatus getStatus() {
        return status;
    }

    public void setStatus(EquipmentStatus status) {
        this.status = status;
    }
}
