package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 操作日志分页条目
 */
public record LogPageItemVO(
        String id,
        String actorId,
        String actorName,
        String actorType,
        String moduleCode,
        String actionCode,
        String targetTable,
        String targetId,
        String requestId,
        String clientIp,
        Integer success,
        Instant occurredAt
) {
}
