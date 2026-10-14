package com.labmanage.module.equipment.enums;

/**
 * 设备物理状态（页面状态由物理状态与当前生效预约派生；报废为最终态）
 */
public enum EquipmentStatus {

    /** 空闲 */
    IDLE,

    /** 已被批准的预约占用 */
    RESERVED,

    /** 借出使用中 */
    IN_USE,

    /** 维护中（损坏登记转入） */
    MAINTAINING,

    /** 报废（最终态） */
    SCRAPPED
}
