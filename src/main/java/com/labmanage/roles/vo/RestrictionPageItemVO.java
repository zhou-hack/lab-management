package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 预约限制分页条目（ID 序列化为字符串；状态由前端按 revokedAt/startAt/endAt 计算）
 */
public record RestrictionPageItemVO(
        String id,
        String userId,
        String userName,
        String accountName,
        String personNo,
        String userType,
        String restrictionType,
        String reason,
        Instant startAt,
        Instant endAt,
        Instant revokedAt,
        Instant createTime
) {
}
