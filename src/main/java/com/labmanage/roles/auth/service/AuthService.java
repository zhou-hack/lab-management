package com.labmanage.roles.auth.service;

import com.labmanage.roles.auth.dto.LoginRequest;
import com.labmanage.roles.auth.vo.LoginVO;
import com.labmanage.roles.auth.vo.UserMenusVO;

/**
 * 认证服务
 */
public interface AuthService {

    /**
     * 账号密码登录：校验锁定与密码，失败计数，成功建立会话
     */
    LoginVO login(LoginRequest request);

    /**
     * 注销当前会话（幂等，未登录调用直接成功）
     */
    void logout();

    /**
     * 当前用户信息 + 角色 + 权限 + 菜单树（M/C/F 格式）
     */
    UserMenusVO userMenus();
}
