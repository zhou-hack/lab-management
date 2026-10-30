package com.labmanage.module.item.vo;

import java.util.List;

public record LocationInventoryVO(
        String locationId,
        String locationCode,
        String locationName,
        List<StockBalanceVO> balances,
        List<FixedAssetVO> assets
) {
}
