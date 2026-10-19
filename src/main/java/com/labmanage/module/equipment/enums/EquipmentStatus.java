package com.labmanage.module.equipment.enums;

/**
 * 设备物理状态（报废为最终态）
 */
public enum EquipmentStatus {

    /** 空闲 */
    IDLE,

    /** 借出中（设备使用申请登记借出发放后进入；归还登记恢复 IDLE 或转 MAINTAINING） */
    IN_USE,

    /** 维护中（损坏登记转入） */
    MAINTAINING,

    /** 报废（最终态） */
    SCRAPPED
}
