package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 名单分页列表项
 *
 * @param matched 是否已完成首登建档（存在引用该名单的有效用户）
 */
public record RosterPageItemVO(
        String id,
        String personNo,
        String personType,
        String realName,
        String phone,
        String departmentId,
        String departmentName,
        Integer enabled,
        Boolean matched,
        Instant createTime
) {
}
