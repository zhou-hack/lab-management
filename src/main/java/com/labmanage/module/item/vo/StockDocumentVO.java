package com.labmanage.module.item.vo;

import java.time.Instant;

public record StockDocumentVO(
        String id,
        String bizDocumentId,
        String documentNo,
        String businessChainId,
        String documentType,
        String businessScene,
        Integer documentSeq,
        String sourceMaterialDocumentId,
        String locationId,
        String locationName,
        String fromLocationId,
        String toLocationId,
        String transferStatus,
        String receivedBy,
        Instant receivedAt,
        String reviewStatus,
        String reviewedBy,
        Instant reviewedAt,
        String reviewReason,
        String operatorId,
        String operatorName,
        String responsibleTeacherId,
        String pickupUserId,
        String pickupUserName,
        Instant occurredAt,
        Instant postedAt,
        String processStatus,
        String returnStatus,
        String overdueReason,
        Integer version,
        String remark,
        Instant createTime
) {
}
