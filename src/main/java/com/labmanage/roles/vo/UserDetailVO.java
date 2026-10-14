package com.labmanage.roles.vo;

import java.time.Instant;
import java.util.List;

/**
 * 用户详情（含角色；密码散列等敏感字段永不输出）
 */
public record UserDetailVO(
        String id,
        String userType,
        String realName,
        String accountName,
        String phone,
        String personNo,
        String departmentId,
        String departmentName,
        Integer enabled,
        Integer failedLoginCount,
        Boolean locked,
        Instant lockedUntil,
        Instant passwordChangedAt,
        Instant createTime,
        List<String> roles
) {
}
