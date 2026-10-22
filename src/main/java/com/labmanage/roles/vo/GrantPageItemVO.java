package com.labmanage.roles.vo;

import java.time.Instant;
import java.util.List;

/**
 * 助理授权分页列表项
 *
 * @param status 展示状态（按时间窗口计算）：ACTIVE生效中/REVOKED已撤销/EXPIRED已过期
 * @param capabilities 授权权力类型（如 MATERIAL_ISSUE 物料领取），由授权范围行聚合
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
        Instant createTime,
        List<String> capabilities
) {
}
