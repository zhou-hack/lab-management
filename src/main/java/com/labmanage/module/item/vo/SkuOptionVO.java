package com.labmanage.module.item.vo;

import java.math.BigDecimal;

/**
 * 物料选项（申领表单选择器用；轻量视图，仅暴露选择所需字段）
 *
 * @param availableQty 可用库存合计（全库位；仅有库存物料才会出现在选项中）
 */
public record SkuOptionVO(
        String id,
        String skuCode,
        String materialName,
        String baseUnit,
        BigDecimal availableQty
) {
}
