package com.labmanage.module.booking.enums;

/**
 * 预约取消类型。
 * v1 仅 MANUAL（本人取消）；FORCE（管理员强制）与 AUTO_EXPIRED（自动过期）
 * 为登记项，随对应迭代启用。
 */
public enum CancelType {

    MANUAL("本人取消"),
    FORCE("管理员强制"),
    AUTO_EXPIRED("自动过期");

    private final String displayName;

    CancelType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
