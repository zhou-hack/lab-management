package com.labmanage.module.booking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.booking.enums.CancelType;
import com.labmanage.module.booking.enums.SeriesStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 场地周期预约规则实体（lab_booking_series）。
 * <p>
 * 老师一次提交「固定星期 + 节次 + 使用周期」，管理员一次批准后批量生成各周
 * 预约单（lab_booking.series_id 回指本规则）与占用槽；周期内各周同节次占用。
 * idempotency_key 联合申请人唯一，防重复提交。
 */
@TableName("lab_booking_series")
public class LabBookingSeries extends BaseEntity {

    /** 预约场地（→ lab.id） */
    private Long labId;

    /** 申请人（→ sys_user.id） */
    private Long applicantId;

    /** 课程名称 */
    private String courseName;

    /** 班级 */
    private String className;

    /** 上课老师（→ sys_user.id） */
    private Long teacherId;

    /** 用途说明 */
    private String purpose;

    /** 参与人数（>0 且不超过场地容量，服务层校验） */
    private Integer participantCount;

    /** 星期：1周一 … 7周日（ISO 8601） */
    private Integer weekday;

    /** 起始节次（→ class_period.id） */
    private Long startPeriodId;

    /** 结束节次（→ class_period.id） */
    private Long endPeriodId;

    /** 周期开始日期（纯日期，上海本地） */
    private LocalDate cycleStartDate;

    /** 周期结束日期（纯日期，须不早于开始日期） */
    private LocalDate cycleEndDate;

    /** 状态：PENDING/APPROVED/REJECTED/CANCELLED */
    private SeriesStatus status;

    /** 驳回原因 */
    private String rejectReason;

    /** 取消原因 */
    private String cancelReason;

    /** 取消类型 */
    private CancelType cancelType;

    /** 取消时间（UTC） */
    private Instant cancelledAt;

    /** 幂等键（申请人维度唯一，防重复提交） */
    private String idempotencyKey;

    public Long getLabId() {
        return labId;
    }

    public void setLabId(Long labId) {
        this.labId = labId;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Integer getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(Integer participantCount) {
        this.participantCount = participantCount;
    }

    public Integer getWeekday() {
        return weekday;
    }

    public void setWeekday(Integer weekday) {
        this.weekday = weekday;
    }

    public Long getStartPeriodId() {
        return startPeriodId;
    }

    public void setStartPeriodId(Long startPeriodId) {
        this.startPeriodId = startPeriodId;
    }

    public Long getEndPeriodId() {
        return endPeriodId;
    }

    public void setEndPeriodId(Long endPeriodId) {
        this.endPeriodId = endPeriodId;
    }

    public LocalDate getCycleStartDate() {
        return cycleStartDate;
    }

    public void setCycleStartDate(LocalDate cycleStartDate) {
        this.cycleStartDate = cycleStartDate;
    }

    public LocalDate getCycleEndDate() {
        return cycleEndDate;
    }

    public void setCycleEndDate(LocalDate cycleEndDate) {
        this.cycleEndDate = cycleEndDate;
    }

    public SeriesStatus getStatus() {
        return status;
    }

    public void setStatus(SeriesStatus status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public CancelType getCancelType() {
        return cancelType;
    }

    public void setCancelType(CancelType cancelType) {
        this.cancelType = cancelType;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}