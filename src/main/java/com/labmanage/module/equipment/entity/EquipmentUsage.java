package com.labmanage.module.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labmanage.common.core.BaseEntity;
import com.labmanage.module.equipment.enums.ReturnCondition;
import com.labmanage.module.equipment.enums.UsageStatus;

import java.time.Instant;

/**
 * 器材借出归还流水实体（booking_id 唯一；追加型，deleted 恒 0）
 */
@TableName("equipment_usage")
public class EquipmentUsage extends BaseEntity {

    /** 借用单（→ equipment_booking.id，唯一） */
    private Long bookingId;

    /** 借用人（→ sys_user.id） */
    private Long borrowerId;

    /** 借出经办人（→ sys_user.id；代借保存实际操作者） */
    private Long checkoutOperatorId;

    /** 借出时间（UTC） */
    private Instant checkedOutAt;

    /** 应还时间（UTC） */
    private Instant dueAt;

    /** 归还经办人（未还为 NULL） */
    private Long returnOperatorId;

    /** 归还时间（UTC；未还为 NULL） */
    private Instant returnedAt;

    /** 归还状态（DAMAGED 须填 return_note） */
    private ReturnCondition returnCondition;

    /** 借出备注 */
    private String checkoutNote;

    /** 归还备注（归还异常/DAMAGED 时必填） */
    private String returnNote;

    /** 流水状态：CHECKED_OUT/RETURNED */
    private UsageStatus status;

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getBorrowerId() {
        return borrowerId;
    }

    public void setBorrowerId(Long borrowerId) {
        this.borrowerId = borrowerId;
    }

    public Long getCheckoutOperatorId() {
        return checkoutOperatorId;
    }

    public void setCheckoutOperatorId(Long checkoutOperatorId) {
        this.checkoutOperatorId = checkoutOperatorId;
    }

    public Instant getCheckedOutAt() {
        return checkedOutAt;
    }

    public void setCheckedOutAt(Instant checkedOutAt) {
        this.checkedOutAt = checkedOutAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Long getReturnOperatorId() {
        return returnOperatorId;
    }

    public void setReturnOperatorId(Long returnOperatorId) {
        this.returnOperatorId = returnOperatorId;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(Instant returnedAt) {
        this.returnedAt = returnedAt;
    }

    public ReturnCondition getReturnCondition() {
        return returnCondition;
    }

    public void setReturnCondition(ReturnCondition returnCondition) {
        this.returnCondition = returnCondition;
    }

    public String getCheckoutNote() {
        return checkoutNote;
    }

    public void setCheckoutNote(String checkoutNote) {
        this.checkoutNote = checkoutNote;
    }

    public String getReturnNote() {
        return returnNote;
    }

    public void setReturnNote(String returnNote) {
        this.returnNote = returnNote;
    }

    public UsageStatus getStatus() {
        return status;
    }

    public void setStatus(UsageStatus status) {
        this.status = status;
    }
}
