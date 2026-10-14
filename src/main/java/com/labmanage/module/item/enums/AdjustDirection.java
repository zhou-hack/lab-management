package com.labmanage.module.item.enums;

/**
 * 盘点调账方向（stock_document_line.adjust_direction 的枚举语义；
 * 实体列为既有 String 字段，本枚举仅在第二阶段服务层使用）
 */
public enum AdjustDirection {
    /** 盘盈（账面量 < 实盘量，调增入库） */
    SURPLUS,
    /** 盘亏（账面量 > 实盘量，调减出库） */
    LOSS
}
