package com.labmanage.module.item.vo;

import java.time.Instant;

public record ItemDocumentEventVO(
        String id,
        String documentId,
        String eventType,
        String fromStatus,
        String toStatus,
        String actorId,
        String actorName,
        String actorType,
        Instant occurredAt,
        String reason,
        String requestKey
) {
}
