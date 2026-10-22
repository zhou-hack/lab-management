package com.labmanage.module.item.enums;

/**
 * 库存单据业务类型
 */
public enum MovementType {
    /** 期初入库 */
    OPENING,
    /** 采购到货入库 */
    PURCHASE_IN,
    /** 领用出库 */
    ISSUE,
    /** 归还入库 */
    RETURN_IN,
    /** 调拨 */
    TRANSFER,
    /** 调账（库存差异调整单据） */
    ADJUST,
    /** 报废 */
    SCRAP,
    /** 冲销 */
    REVERSAL
}
