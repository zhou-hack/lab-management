package com.labmanage.module.item.vo;

import java.math.BigDecimal;

public record StockDocumentLineVO(
        String id,
        String materialDocumentId,
        String claimLineId,
        String sourceMaterialDocumentLineId,
        String materialId,
        String materialName,
        String specification,
        String baseUnit,
        String fixedAssetId,
        String assetCode,
        BigDecimal quantity,
        String positionText
) {
}
