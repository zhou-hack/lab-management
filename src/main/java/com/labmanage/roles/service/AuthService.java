package com.labmanage.roles.service;

import com.labmanage.roles.dto.AccountLoginDTO;
import com.labmanage.roles.dto.PasswordConfirmDTO;
import com.labmanage.roles.dto.PasswordUpdateDTO;
import com.labmanage.roles.dto.SmsLoginDTO;
import com.labmanage.roles.dto.SmsSendDTO;
import com.labmanage.roles.dto.SwitchRoleDTO;
import com.labmanage.roles.vo.ConfirmVO;
import com.labmanage.roles.vo.LoginVO;
import com.labmanage.roles.vo.ProfileVO;
import com.labmanage.roles.vo.SmsSendVO;

import java.util.List;

/**
 * 认证服务：登录、首登建档、身份装配、密码管理与注销。
 */
public interface AuthService {

    /** Sa-Token 会话缓存键：角色码集合 */
    String SESSION_ROLES = "auth_roles";

    /** Sa-Token 会话缓存键：权限标识集合 */
    String SESSION_PERMS = "auth_perms";

    /** Sa-Token 会话缓存键：活动角色码（当前生效的身份视图） */
    String SESSION_ACTIVE_ROLE = "auth_active_role";

    /** 账号密码登录（管理员） */
    LoginVO login(AccountLoginDTO dto);

    /** 发送短信验证码 */
    SmsSendVO sendSmsCode(SmsSendDTO dto);

    /** 验证码登录；无账号时匹配预录名单自动建档并派生角色 */
    LoginVO smsLogin(SmsLoginDTO dto);

    /** 当前身份（角色/权限/菜单，供动态路由与 v-permission） */
    ProfileVO profile();

    /** 切换活动角色（多角色用户会话内切换身份视图，重新装配并返回新身份） */
    ProfileVO switchRole(SwitchRoleDTO dto);

    /** 设置/修改密码 */
    void updatePassword(PasswordUpdateDTO dto);

    /** 敏感操作二次密码确认（开启 Sa-Token 二级认证） */
    ConfirmVO confirmPassword(PasswordConfirmDTO dto);

    /** 注销当前会话 */
    void logout();

    /** 用户角色码集合：持久化角色 + 有效助理授权动态计算的 ASSISTANT */
    List<String> loadRoleCodes(Long userId);

    /** 用户权限标识集合（持久化角色绑定；ASSISTANT 增量随物料模块落地） */
    List<String> loadPermissionCodes(Long userId);

    /** StpInterface 兜底：按会话活动角色装配角色码集合并回写会话缓存 */
    List<String> refreshSessionRoles(Long userId);

    /** StpInterface 兜底：按会话活动角色装配权限标识集合并回写会话缓存 */
    List<String> refreshSessionPermissions(Long userId);

    /** 权限/名单/范围变更后踢掉会话强制重载（决策：变更踢会话） */
    void evictSession(Long userId);
}
