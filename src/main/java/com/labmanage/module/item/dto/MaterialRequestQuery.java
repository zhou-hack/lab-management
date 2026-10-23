package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;
import com.labmanage.module.item.enums.MaterialRequestStatus;
import com.labmanage.module.item.enums.RequestType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 物料申领单分页查询参数
 */
@Schema(description = "物料申领单分页查询参数")
public class MaterialRequestQuery extends BasePageQuery {

    @Schema(description = "状态", example = "APPROVED")
    private MaterialRequestStatus status;

    @Schema(description = "申领类型：TEACHER 老师预约 / PROXY 学生代理代领", example = "TEACHER")
    private RequestType requestType;

    @Schema(description = "领用实验室 ID", example = "1234567890123456789")
    private String labId;

    @Schema(description = "归属老师 ID", example = "1234567890123456789")
    private String ownerTeacherId;

    @Schema(description = "开课实例 ID", example = "1234567890123456789")
    private String offeringId;

    @Schema(description = "课程名称（按关联场地预约的课程/班级名称包含匹配）", example = "电力电子")
    private String courseName;

    public MaterialRequestStatus getStatus() {
        return status;
    }

    public void setStatus(MaterialRequestStatus status) {
        this.status = status;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public String getLabId() {
        return labId;
    }

    public void setLabId(String labId) {
        this.labId = labId;
    }

    public String getOwnerTeacherId() {
        return ownerTeacherId;
    }

    public void setOwnerTeacherId(String ownerTeacherId) {
        this.ownerTeacherId = ownerTeacherId;
    }

    public String getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(String offeringId) {
        this.offeringId = offeringId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }
}
