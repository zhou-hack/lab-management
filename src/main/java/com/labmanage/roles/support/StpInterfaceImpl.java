package com.labmanage.roles.support;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.labmanage.roles.service.AuthService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限/角色数据源：优先读会话缓存，未命中实时装配并回写；
 * 名单、范围、授权等变更通过 {@link AuthService#evictSession} 踢会话强制重载。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final AuthService authService;

    public StpInterfaceImpl(AuthService authService) {
        this.authService = authService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getPermissionList(Object loginId, String loginType) {
        Object cached = StpUtil.getSession().get(AuthService.SESSION_PERMS);
        if (cached instanceof List<?>) {
            return (List<String>) cached;
        }
        // 兜底实时装配（按会话活动角色），refresh 内部已回写会话缓存
        return authService.refreshSessionPermissions(Long.valueOf(loginId.toString()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getRoleList(Object loginId, String loginType) {
        Object cached = StpUtil.getSession().get(AuthService.SESSION_ROLES);
        if (cached instanceof List<?>) {
            return (List<String>) cached;
        }
        // 兜底实时装配（按会话活动角色），refresh 内部已回写会话缓存
        return authService.refreshSessionRoles(Long.valueOf(loginId.toString()));
    }
}
