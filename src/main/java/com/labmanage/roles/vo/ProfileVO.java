package com.labmanage.roles.vo;

import java.util.List;

/**
 * 当前身份响应（登录后 / 刷新时加载动态路由与权限）
 * <p>
 * identities：当前账号可切换的全部身份（一键切换下拉数据源），首项为主身份；
 * currentIdentity：当前会话生效身份码（未切换时等于 userType）。
 */
public record ProfileVO(
        String userId,
        String realName,
        String userType,
        List<String> roles,
        List<String> permissions,
        List<MenuVO> menus,
        List<IdentityOptionVO> identities,
        String currentIdentity
) {
}