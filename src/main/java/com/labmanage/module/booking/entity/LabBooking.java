package com.labmanage.module.booking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.booking.enums.BookingStatus;
import com.labmanage.module.booking.enums.CancelType;

import java.time.Instant;

/**
 * 场地预约单实体（lab_booking）。
 * <p>
 * start_at/end_at 为 UTC 时间点（Instant，DATETIME(3) 存储）；
 * 审批通过在 lab_slot 写入占用；APPROVED 取消时同步释放占用。
 */
@TableName("lab_booking")
public class LabBooking extends BaseEntity {

    /** 场地（→ biz_space.id，单表模式下 lab 即场地） */
    private Long labId;

    /** 申请人（→ sys_user.id） */
    private Long applicantId;

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
