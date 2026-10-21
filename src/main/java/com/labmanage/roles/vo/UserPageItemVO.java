package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 用户分页列表项
 *
 * @param locked     当前是否处于锁定期（由 locked_until 与 UTC 当前时间比较得出）
 * @param adminBound 是否已绑定 ADMIN 角色（老师账号的绑定/解绑入口展示依据）
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
        Boolean adminBound,
        Instant createTime
) {
}
