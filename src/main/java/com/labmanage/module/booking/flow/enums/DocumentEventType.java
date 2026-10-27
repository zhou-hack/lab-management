package com.labmanage.module.booking.flow.enums;

/**
 * 场地预约单据事件类型（document_event.event_type，追加型不可变历史）。
 * <p>
 * 权级流程：SUBMIT 提交 → APPROVE/REJECT 管理员核对裁决 →
 * CHECKIN 开门签到 → CHECKOUT 结束清点；CANCEL 本人撤销、
 * TIMEOUT_CANCEL 超时未批系统自动取消；FORCE_CANCEL 管理员强制取消（A2）、
 * AUTO_FINISH 超时未清点系统自动完结（A1）。
 */
public enum DocumentEventType {

    SUBMIT("提交申请"),
    APPROVE("审批通过"),
    REJECT("驳回"),
    CANCEL("撤销"),
    CHECKIN("开门签到"),
    CHECKOUT("结束清点"),
    TIMEOUT_CANCEL("超时自动取消"),
    FORCE_CANCEL("管理员强制取消"),
    AUTO_FINISH("超时自动完结");

    private final String displayName;

    DocumentEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
