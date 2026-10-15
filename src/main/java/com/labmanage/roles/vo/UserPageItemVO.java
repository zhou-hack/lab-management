package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 用户分页列表项
 *
 * @param locked 当前是否处于锁定期（由 locked_until 与 UTC 当前时间比较得出）
 */
public record UserPageItemVO(
        String id,
        String userType,
        String realName,
        String accountName,
        String phone,
        String personNo,
        String departmentId,
        String departmentName,
        Integer enabled,
        Boolean locked,
        Instant createTime
) {
}
