package com.labmanage.common.security;

import java.time.Instant;
import java.util.List;

/**
 * 当前登录用户（由 JWT 过滤器装配进 AuthContext）。
 *
 * @param userId      用户 ID（JWT sub）
 * @param activeRole  活动角色（当前生效的身份视图；多角色用户可经 /auth/switch_role 切换）
 * @param roles       角色码集合（活动角色 + 动态 ASSISTANT；每请求实时加载，不随 token 缓存）
 * @param permissions 权限标识集合（按活动角色装配，每请求实时加载）
 * @param safeUntil   敏感操作二次认证有效截止（null=未通过二级认证）
 */
public record LoginUser(Long userId, String activeRole, List<String> roles, List<String> permissions, Instant safeUntil) {

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /** 二级认证是否在有效期内 */
    public boolean isSafeAuthed() {
        return safeUntil != null && safeUntil.isAfter(Instant.now());
    }

    /** 开启/延长二级认证有效期 */
    public LoginUser withSafeUntil(Instant until) {
        return new LoginUser(userId, activeRole, roles, permissions, until);
    }
}
