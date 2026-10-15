package com.labmanage.module.equipment.enums;

/**
 * 设备物理状态（报废为最终态）
 */
public enum EquipmentStatus {

    /** 空闲 */
    IDLE,

    /** 维护中（损坏登记转入） */
    MAINTAINING,

    /** 报废（最终态） */
    SCRAPPED
}
