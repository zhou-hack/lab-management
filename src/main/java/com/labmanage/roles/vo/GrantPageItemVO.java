package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 助理授权分页列表项
 *
 * @param status 展示状态（按时间窗口计算）：ACTIVE生效中/REVOKED已撤销/EXPIRED已过期
 */
public record GrantPageItemVO(
        String id,
        String teacherId,
        String teacherName,
        String studentId,
        String studentName,
        String studentNo,
        Instant startAt,
        Instant endAt,
        String status,
        Instant revokedAt,
        String revokedByName,
        String reason,
        Instant createTime
) {
}
