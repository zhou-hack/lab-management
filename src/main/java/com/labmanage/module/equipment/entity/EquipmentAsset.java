package com.labmanage.module.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.equipment.enums.EquipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 设备台账实体（资产编号唯一，每台/每套一行）
 */
@TableName("equipment_asset")
public class EquipmentAsset extends BaseEntity {

    /** 设备型号（→ equipment_model.id） */
    private Long modelId;

    /** 资产编号（唯一，删除后不复用） */
    private String assetCode;

    /** 序列号（不强制全局唯一） */
    private String serialNo;

    /** 归口部门（→ sys_department.id） */
    private Long departmentId;

    /** 室内存放位置说明 */
    private String locationDetail;

    /** 保管人（→ sys_user.id） */
    private Long custodianId;

    /** 采购日期（纯日期） */
    private LocalDate purchaseDate;

    /** 采购单价（MONEY） */
    private BigDecimal purchasePrice;

    /** 保修截止（纯日期） */
    private LocalDate warrantyUntil;

    /** 设备状态（报废为最终态） */
    private EquipmentStatus status;

    /** 启用：1启用 0停用 */
    private Integer enabled;

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
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

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public LocalDate getWarrantyUntil() {
        return warrantyUntil;
    }

    public void setWarrantyUntil(LocalDate warrantyUntil) {
        this.warrantyUntil = warrantyUntil;
    }

    public EquipmentStatus getStatus() {
        return status;
    }

    public void setStatus(EquipmentStatus status) {
        this.status = status;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
