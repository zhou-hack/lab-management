package com.labmanage.module.item.vo;

import java.time.Instant;

public record TransferSummaryVO(
        String id,
        String bizDocumentId,
        String documentNo,
        String fromLocationId,
        String fromLocationName,
        String toLocationId,
        String toLocationName,
        String transferStatus,
        String operatorId,
        String operatorName,
        Instant occurredAt,
        Instant postedAt,
        String receivedBy,
        Instant receivedAt,
        Integer version,
        String remark,
        Instant createTime
) {
}
