package com.labmanage.roles.vo;

import java.time.Instant;
import java.util.List;

/**
 * 助理授权记录视图。id/关联 id 序列化为字符串避免 JS 精度丢失。
 * status：ACTIVE（生效中）/ REVOKED（已撤销）/ EXPIRED（已到期，查询时按 endAt 派生）。
 */
public record GrantVO(
        String id,
        String studentId,
        String studentName,
        String studentNo,
        Instant startAt,
        Instant endAt,
        String status,
        String reason,
        List<GrantScopeVO> scopes,
        Instant createTime
) {

    /**
     * 授权范围视图：labName 由 lab 表联查补齐。
     */
    public record GrantScopeVO(
            String id,
            String scopeType,
            String labId,
            String labName,
            String actionCode
    ) {
    }
}
