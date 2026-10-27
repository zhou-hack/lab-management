package com.labmanage.module.booking.enums;

/**
 * 预约取消类型。
 * MANUAL（本人取消）、FORCE（管理员强制取消，A2 已启用）、AUTO_EXPIRED（自动过期/超时）。
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
