package com.labmanage.module.document.enums;

/**
 * 单据事件类型（不可变过程历史）
 */
public enum EventType {

    /** 提交 */
    SUBMIT,

    /** 审批通过 */
    APPROVE,

    /** 审批驳回 */
    REJECT,

    /** 撤销 */
    CANCEL,

    /** 借出确认 */
    CHECKOUT,

    /** 归还确认 */
    RETURN
}
