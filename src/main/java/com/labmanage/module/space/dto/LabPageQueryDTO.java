package com.labmanage.module.space.dto;

import com.labmanage.common.core.BasePageQuery;
import jakarta.validation.constraints.Size;

/**
 * 实验室分页查询参数
 */
public class LabPageQueryDTO extends BasePageQuery {

    /** 关键字：模糊匹配实验室编码 / 名称 / 房间号 */
    @Size(max = 64, message = "关键字长度不能超过 64")
    private String keyword;

    /** 按校区过滤（联动楼栋） */
    private Long campusId;

    /** 按楼栋过滤 */
    private Long buildingId;

    /** 按管理部门过滤 */
    private Long departmentId;

    /** 类型精确筛选：TEACHING/RESEARCH/OPEN/COMPREHENSIVE */
    @Size(max = 32, message = "实验室类型长度不能超过 32")
    private String labType;

    /** 启用状态精确筛选：0/1 */
    private Integer enabled;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCampusId() {
        return campusId;
    }

    public void setCampusId(Long campusId) {
        this.campusId = campusId;
    }

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

    public String getLabType() {
        return labType;
    }

    public void setLabType(String labType) {
        this.labType = labType;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
