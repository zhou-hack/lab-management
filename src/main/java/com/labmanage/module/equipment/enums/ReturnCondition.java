package com.labmanage.module.equipment.enums;

/**
 * 归还状态（DAMAGED 须填写归还备注并将设备转入维护中）
 */
public enum ReturnCondition {

    /** 完好（设备恢复空闲） */
    NORMAL,

    /** 损坏（设备转维护中，必填备注） */
    DAMAGED
}
