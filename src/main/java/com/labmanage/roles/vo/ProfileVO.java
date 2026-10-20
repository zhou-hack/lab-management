package com.labmanage.roles.vo;

import java.util.List;

/**
 * 当前身份响应（登录后 / 刷新时加载角色与权限）
 */
public record ProfileVO(
        String userId,
        String realName,
        String userType,
        List<String> roles,
        List<String> permissions,
        List<MenuVO> menus
) {
}
