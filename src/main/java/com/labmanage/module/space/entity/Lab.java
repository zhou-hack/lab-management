package com.labmanage.module.space.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;

import java.math.BigDecimal;

/**
 * 实验室实体（实验室码全局唯一，同楼栋房间号唯一）
 */
@TableName("lab")
public class Lab extends BaseEntity {

    /** 所在楼栋（→ building.id） */
    private Long buildingId;

    /** 归口部门（→ sys_department.id） */
    private Long departmentId;

    /** 实验室编码（全局唯一，删除后不复用） */
    private String labCode;

    /** 实验室名称 */
    private String labName;

    /** 实验室类型（业务字典，如 TEACHING/RESEARCH） */
    private String labType;

    /** 楼层号 */
    private String floorNo;

    /** 房间号（同楼栋唯一） */
    private String roomNo;

    /** 容纳人数（大于 0） */
    private Integer capacity;

    /** 面积（平方米） */
    private BigDecimal areaSqm;

    /** 位置说明 */
    private String locationNote;

    /** 启用：1启用 0停用 */
    private Integer enabled;

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

    public String getLabCode() {
        return labCode;
    }

    public void setLabCode(String labCode) {
        this.labCode = labCode;
    }

    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }

    public String getLabType() {
        return labType;
    }

    public void setLabType(String labType) {
        this.labType = labType;
    }

    public String getFloorNo() {
        return floorNo;
    }

    public void setFloorNo(String floorNo) {
        this.floorNo = floorNo;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public BigDecimal getAreaSqm() {
        return areaSqm;
    }

    public void setAreaSqm(BigDecimal areaSqm) {
        this.areaSqm = areaSqm;
    }

    public String getLocationNote() {
        return locationNote;
    }

    public void setLocationNote(String locationNote) {
        this.locationNote = locationNote;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
