package com.labmanage.module.item.vo;

import java.util.List;

public record StockDocumentDetailVO(
        StockDocumentVO document,
        List<StockDocumentLineVO> lines,
        List<StockSettlementVO> settlements,
        List<ItemDocumentEventVO> events
) {
}
