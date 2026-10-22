package com.labmanage.module.equipment.enums;

/**
 * 设备价值分级（价值分级已从台账移除；本枚举仅 equipment_booking.value_level_snapshot
 * 保留列使用，新单恒为 NORMAL）
 */
public enum ValueLevel {

    /** 普通 */
    NORMAL,

    /** 贵重 */
    VALUABLE
}
