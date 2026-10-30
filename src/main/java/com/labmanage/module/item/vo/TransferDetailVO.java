package com.labmanage.module.item.vo;

import java.math.BigDecimal;
import java.util.List;

public record TransferDetailVO(
        TransferSummaryVO document,
        BigDecimal remainingQuantity,
        List<Line> lines,
        List<ItemDocumentEventVO> events,
        boolean canReceive,
        boolean canReturnRequest,
        boolean canReturnReceive,
        boolean canWriteoff,
        boolean canReview
) {
    public record Line(
            String sourceMaterialDocumentLineId,
            String materialId,
            String materialName,
            String specification,
            String baseUnit,
            String fixedAssetId,
            String assetCode,
            String positionText,
            BigDecimal sentQuantity,
            BigDecimal receivedQuantity,
            BigDecimal returnedQuantity,
            BigDecimal settledQuantity,
            BigDecimal remainingQuantity
    ) {
    }
}
