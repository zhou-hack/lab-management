package com.labmanage.module.item.enums;

/**
 * 物料类型
 */
public enum MaterialType {
    /** 耗材（实际发出后登记 CONSUMED 核销，不要求归还） */
    CONSUMABLE,
    /** 可回收（出库后跟踪归还与核销） */
    RETURNABLE
}
