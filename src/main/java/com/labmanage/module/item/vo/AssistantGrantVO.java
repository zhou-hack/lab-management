package com.labmanage.module.item.vo;

import java.time.Instant;

public record AssistantGrantVO(
        String id,
        String teacherId,
        String teacherName,
        String studentId,
        String studentName,
        String studentAccount,
        String studentNo,
        Instant startAt,
        Instant endAt,
        String status,
        Instant revokedAt,
        String revokedBy,
        String reason,
        Instant createTime
) {
}
