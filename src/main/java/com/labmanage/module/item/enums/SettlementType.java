package com.labmanage.module.item.enums;

/**
 * 物料结算类型
 */
public enum SettlementType {
    /** 归还（随 RETURN_IN 过账同事务生成，不走核销接口） */
    RETURNED,
    /** 耗用（耗材实际发出后核销，不回库） */
    CONSUMED,
    /** 丢失（不再扣库存，出库时已扣减） */
    LOST,
    /** 损坏（不重新增加可用库存，恢复需独立批准的入库动作） */
    DAMAGED
}
