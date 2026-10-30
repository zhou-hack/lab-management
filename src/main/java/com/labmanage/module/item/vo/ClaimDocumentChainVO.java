package com.labmanage.module.item.vo;

import java.util.List;

public record ClaimDocumentChainVO(
        String claimId,
        String businessChainId,
        List<StockDocumentDetailVO> documents
) {
}
