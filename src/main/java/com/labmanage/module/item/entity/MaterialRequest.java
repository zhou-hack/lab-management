package com.labmanage.module.item.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.item.enums.MaterialRequestStatus;
import com.labmanage.module.item.enums.RequestType;

import java.time.Instant;

/**
 * 物料申领单（申领批准不扣库存，实际出库另行过账）
 */
@TableName("material_request")
public class MaterialRequest extends BaseEntity {

    /** 统一单据 ID（单据中心建成前由本模块预生成雪花 ID 占位） */
    private Long documentId;

    /** 领用实验室 ID（空间模块建成前允许 NULL） */
    private Long labId;

    /** 归属老师 ID（登录模块接入后由会话写入；PROXY 时代理代领绑定的责任老师，取源申领单归属老师） */
    private Long ownerTeacherId;

    /** 经办人 ID（学生代理代领时为代理学生本人） */
    private Long operatorId;

    /** 命中的助理授权 ID */
    private Long grantId;

    /** 课程实例 ID（课程模块建成前允许 NULL） */
    private Long offeringId;

    /** 场地预约 ID（与课次恰一非空，模块接入后收紧） */
    private Long labBookingId;

    /** 课次 ID（不能与预约同时填） */
    private Long scheduleOccurrenceId;

    /** 参与人数（非负） */
    private Integer participantCount;

    /** 课时数（非负） */
    private Integer periodCount;

    /** 用途 */
    private String purpose;

    /** 申领类型：SELF 本人申领 / PROXY 学生代理代领 */
    private RequestType requestType;

    /** 源申领单 ID（PROXY 时引用的老师已审核通过的申领单） */
    private Long sourceRequestId;

    /** 状态 */
    private MaterialRequestStatus status;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getLabId() {
        return labId;
    }

    public void setLabId(Long labId) {
        this.labId = labId;
    }

    public Long getOwnerTeacherId() {
        return ownerTeacherId;
    }

    public void setOwnerTeacherId(Long ownerTeacherId) {
        this.ownerTeacherId = ownerTeacherId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getGrantId() {
        return grantId;
    }

    public void setGrantId(Long grantId) {
        this.grantId = grantId;
    }

    public Long getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(Long offeringId) {
        this.offeringId = offeringId;
    }

    public Long getLabBookingId() {
        return labBookingId;
    }

    public void setLabBookingId(Long labBookingId) {
        this.labBookingId = labBookingId;
    }

    public Long getScheduleOccurrenceId() {
        return scheduleOccurrenceId;
    }

    public void setScheduleOccurrenceId(Long scheduleOccurrenceId) {
        this.scheduleOccurrenceId = scheduleOccurrenceId;
    }

    public Integer getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(Integer participantCount) {
        this.participantCount = participantCount;
    }

    public Integer getPeriodCount() {
        return periodCount;
    }

    public void setPeriodCount(Integer periodCount) {
        this.periodCount = periodCount;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public Long getSourceRequestId() {
        return sourceRequestId;
    }

    public void setSourceRequestId(Long sourceRequestId) {
        this.sourceRequestId = sourceRequestId;
    }

    public MaterialRequestStatus getStatus() {
        return status;
    }

    public void setStatus(MaterialRequestStatus status) {
        this.status = status;
    }

    /** 便捷判断：状态是否允许出库实发（APPROVED 后含部分实发） */
    public boolean issuable() {
        return status == MaterialRequestStatus.APPROVED
                || status == MaterialRequestStatus.PARTIALLY_FULFILLED;
    }

    /** 便捷判断：单据是否处于可取消状态 */
    public boolean cancellable() {
        return status == MaterialRequestStatus.DRAFT
                || status == MaterialRequestStatus.PENDING
                || status == MaterialRequestStatus.TEACHER_APPROVED
                || status == MaterialRequestStatus.APPROVED
                || status == MaterialRequestStatus.PARTIALLY_FULFILLED;
    }

    /** 便捷判断：是否有已实发量（取消前须无实发） */
    public boolean hasFulfillment() {
        return status == MaterialRequestStatus.PARTIALLY_FULFILLED
                || status == MaterialRequestStatus.FULFILLED;
    }

    /** 登记时间（等价 createTime 语义，便于统一单据口径） */
    public Instant registeredAt() {
        return getCreateTime();
    }
}
