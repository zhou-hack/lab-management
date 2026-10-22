package com.labmanage.module.booking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.booking.enums.BookingStatus;
import com.labmanage.module.booking.enums.CancelType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 场地预约单实体（lab_booking）。
 * <p>
 * start_at/end_at 为 UTC 时间点（Instant，DATETIME(3) 存储）；
 * 审批通过在 lab_slot 写入占用；APPROVED 取消时同步释放占用。
 * <p>
 * 节次时间轴扩展（2026-10-20）：booking_date/start_period_id/end_period_id
 * 记录预约日期与节次（时间轴三色小卡片数据源），course_name/class_name/
 * teacher_id 记录课程/班级/上课老师；series_id 回指周期预约规则
 * （lab_booking_series）。历史单及纯时间点提交单这些列为 NULL，兼容。
 */
@TableName("lab_booking")
public class LabBooking extends BaseEntity {

    /** 场地（→ lab.id，单表模式下 lab 即场地） */
    private Long labId;

    /** 申请人（→ sys_user.id） */
    private Long applicantId;

    /** 预约日期（纯日期，上海本地日界；周期/节次预约必填） */
    private LocalDate bookingDate;

    /** 起始节次（→ class_period.id；节次预约必填） */
    private Long startPeriodId;

    /** 结束节次（→ class_period.id；节次预约必填） */
    private Long endPeriodId;

    /** 来源周期规则（→ lab_booking_series.id；单次预约为 NULL） */
    private Long seriesId;

    /** 课程名称（提交预约时填写） */
    private String courseName;

    /** 班级（提交预约时填写） */
    private String className;

    /** 上课老师（→ sys_user.id） */
    private Long teacherId;

    /** 使用事由 */
    private String purpose;

    /** 参与人数（>0 且不超过场地容量，服务层校验） */
    private Integer participantCount;

    /** 预约开始（UTC 时间点） */
    private Instant startAt;

    /** 预约结束（UTC 时间点，须晚于 startAt） */
    private Instant endAt;

    /** 状态：PENDING/APPROVED/REJECTED/CANCELLED */
    private BookingStatus status;

    /** 背书老师（学生预约链路预留，一期不使用） */
    private Long endorseTeacherId;

    /** 背书时间（预留） */
    private Instant endorseAt;

    /** 背书意见（预留） */
    private String endorseComment;

    /** 驳回原因 */
    private String rejectReason;

    /** 取消原因 */
    private String cancelReason;

    /** 取消类型：MANUAL/FORCE（AUTO_EXPIRED 预留） */
    private CancelType cancelType;

    /** 取消时间（UTC） */
    private Instant cancelledAt;

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

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
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

    public Long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
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

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public Long getEndorseTeacherId() {
        return endorseTeacherId;
    }

    public void setEndorseTeacherId(Long endorseTeacherId) {
        this.endorseTeacherId = endorseTeacherId;
    }

    public Instant getEndorseAt() {
        return endorseAt;
    }

    public void setEndorseAt(Instant endorseAt) {
        this.endorseAt = endorseAt;
    }

    public String getEndorseComment() {
        return endorseComment;
    }

    public void setEndorseComment(String endorseComment) {
        this.endorseComment = endorseComment;
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
}
