package com.labmanage.module.booking.enums;

/**
 * 场地预约单状态（v1 状态机）。
 * <p>
 * PENDING → APPROVED / REJECTED；PENDING / APPROVED → CANCELLED
 * （APPROVED 取消同步释放 lab_slot 占用）。
 * IN_USE / FINISHED 不落库，由接口按当前时间派生展示（end_at+2h 为归档宽限）。
 */
public enum BookingStatus {

    PENDING("待审批"),
    APPROVED("已批准"),
    REJECTED("已驳回"),
    CANCELLED("已取消");

    private final String displayName;

    BookingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
