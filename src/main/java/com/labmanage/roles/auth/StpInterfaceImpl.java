package com.labmanage.roles.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.labmanage.roles.user.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源
 * <p>
 * 每次鉴权实时查询角色与权限（启用与逻辑删除条件在 SQL 内收敛）；
 * auth 模块落地后按需接入缓存与 ASSISTANT 动态角色（assistant_grant）。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final SysUserMapper sysUserMapper;

    public StpInterfaceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return sysUserMapper.selectPermissionCodes(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return sysUserMapper.selectRoleCodes(Long.valueOf(loginId.toString()));
    }
}
