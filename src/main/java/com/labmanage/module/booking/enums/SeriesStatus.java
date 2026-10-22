package com.labmanage.module.booking.enums;

/**
 * 场地周期预约规则状态。
 * <p>
 * PENDING（待管理员审批，暂不生成预约单与占用）→ APPROVED（逐周生成预约单
 * 与占用槽）/ REJECTED（整单驳回）；PENDING / APPROVED → CANCELLED。
 * 与单次预约共用一期审批链：仅管理员可批准/驳回，申请人不审自己的单。
 */
public enum SeriesStatus {

    PENDING("待审批"),
    APPROVED("已批准"),
    REJECTED("已驳回"),
    CANCELLED("已取消");

    private final String displayName;

    SeriesStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}