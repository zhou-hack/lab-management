package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 数据范围分页条目（ID 序列化为字符串）
 */
public record ScopePageItemVO(
        String id,
        String userId,
        String userName,
        String accountName,
        String scopeType,
        String departmentId,
        String departmentName,
        String accessMode,
        Instant createTime
) {
}
