package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 统一人员视图分页列表项（名单与账号合并，一行一人）
 *
 * @param userId 账号 ID（未建档为 null）
 * @param rosterId 名单 ID（纯后台账号为 null）
 * @param status UNFILED 未建档 / ROSTER_DISABLED 名单停用 / NORMAL 正常 / USER_DISABLED 账号停用
 * @param roles 账号持有的持久化角色码（逗号分隔；无账号为 null；ASSISTANT 由授权动态计算不在此列）
 */
public record PersonPageItemVO(
        String userId,
        String rosterId,
        String personNo,
        String accountName,
        String realName,
        String phone,
        String departmentId,
        String departmentName,
        String personType,
        String status,
        String roles,
        Instant createTime
) {
}
