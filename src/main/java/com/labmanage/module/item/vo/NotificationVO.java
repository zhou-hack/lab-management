package com.labmanage.module.item.vo;

import java.time.Instant;
import java.time.LocalDate;

public record NotificationVO(
        String id,
        String dedupeKey,
        String recipientUserId,
        String notificationType,
        String notificationStage,
        LocalDate reminderDate,
        String claimId,
        String materialDocumentId,
        String bizDocumentId,
        String title,
        String content,
        Instant readAt,
        Instant createTime
) {
}
