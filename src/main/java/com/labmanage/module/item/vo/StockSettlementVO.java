package com.labmanage.module.item.vo;

import java.math.BigDecimal;

public record StockSettlementVO(
        String id,
        String materialDocumentId,
        String sourceMaterialDocumentLineId,
        String materialId,
        String materialName,
        String fixedAssetId,
        String assetCode,
        String settlementType,
        BigDecimal quantity,
        String targetAssetStatus,
        String reason
) {
}
