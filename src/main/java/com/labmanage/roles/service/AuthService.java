package com.labmanage.roles.service;

import com.labmanage.common.security.LoginUser;
import com.labmanage.roles.dto.AccountLoginDTO;
import com.labmanage.roles.dto.PasswordConfirmDTO;
import com.labmanage.roles.dto.RefreshTokenDTO;
import com.labmanage.roles.dto.SwitchRoleDTO;
import com.labmanage.roles.vo.ConfirmVO;
import com.labmanage.roles.vo.LoginVO;
import com.labmanage.roles.vo.ProfileVO;
import com.labmanage.roles.vo.SaltVO;

import java.util.List;

/**
 * 认证服务：取盐、账号密码登录、token 刷新、身份装配、活动角色切换与注销。
 * <p>
 * 口令传输约定（2026-10-20 定稿）：客户端先按账号预取 BCrypt 盐，
 * 本地算出与库中一致的哈希后上传，服务端常量时间比对，全程不传输明文密码。
 */
public interface AuthService {

    /** 按账号预取 BCrypt 盐（登录/二级确认前调用） */
    SaltVO salt(String accountName);

    /** 账号密码登录（password 为客户端 bcrypt 密文） */
    LoginVO login(AccountLoginDTO dto);

    /** refresh token 换新（签发新 access+refresh 对，保留活动角色） */
    LoginVO refresh(RefreshTokenDTO dto);

    /** 当前身份（按活动角色装配角色/权限/菜单） */
    ProfileVO profile();

    /** 切换活动角色（多角色用户切换身份视图，重签 token 对并返回新身份装配） */
    LoginVO switchRole(SwitchRoleDTO dto);

    /** 敏感操作二次密码确认（password 为客户端 bcrypt 密文，通过后开启二级认证有效期） */
    ConfirmVO confirmPassword(PasswordConfirmDTO dto);

    /** 注销（无状态 JWT：服务端无会话可清，客户端丢弃 token 即可） */
    void logout();

    /**
     * 每请求装配登录态：校验 token 中的活动角色声明（失效/缺失时回退默认活动角色），
     * 按活动角色实时加载角色与权限。
     */
    LoginUser buildLoginUser(Long userId, String claimedActiveRole);

    /** 用户全部持有角色码集合：持久化角色 + 有效助理授权动态计算的 ASSISTANT */
    List<String> loadRoleCodes(Long userId);

    /** 用户权限标识集合（全部持有角色并集；活动角色过滤见 buildLoginUser） */
    List<String> loadPermissionCodes(Long userId);

    /**
     * 权限/名单/范围变更后使凭据尽快失效。
     * 无状态 JWT 无法主动踢会话；角色权限每请求实时加载，access token 最长 2h 自然过期。
     */
    void evictSession(Long userId);
}
