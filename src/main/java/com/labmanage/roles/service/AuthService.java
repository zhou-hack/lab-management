package com.labmanage.roles.service;

import com.labmanage.roles.dto.AccountLoginDTO;
import com.labmanage.roles.dto.IdentitySwitchDTO;
import com.labmanage.roles.dto.PasswordConfirmDTO;
import com.labmanage.roles.dto.PasswordUpdateDTO;
import com.labmanage.roles.dto.SmsLoginDTO;
import com.labmanage.roles.dto.SmsSendDTO;
import com.labmanage.roles.vo.ConfirmVO;
import com.labmanage.roles.vo.LoginVO;
import com.labmanage.roles.vo.ProfileVO;
import com.labmanage.roles.vo.SmsSendVO;

import java.util.List;

/**
 * 认证服务：登录、首登建档、身份装配、身份一键切换、密码管理与注销。
 */
public interface AuthService {

    /** Sa-Token 会话缓存键：角色码集合 */
    String SESSION_ROLES = "auth_roles";

    /** Sa-Token 会话缓存键：权限标识集合 */
    String SESSION_PERMS = "auth_perms";

    /** Sa-Token 会话缓存键：当前生效身份码（未切换时为 null，回退主身份） */
    String SESSION_CURRENT_IDENTITY = "current_identity";

    /** 账号密码登录（管理员） */
    LoginVO login(AccountLoginDTO dto);

    /** 发送短信验证码 */
    SmsSendVO sendSmsCode(SmsSendDTO dto);

    /** 验证码登录；无账号时匹配预录名单自动建档并派生角色 */
    LoginVO smsLogin(SmsLoginDTO dto);

    /** 当前身份（角色/权限/菜单 + 可切换身份列表，供动态路由与 v-permission） */
    ProfileVO profile();

    /** 身份一键切换：校验目标身份可用后按身份重算角色/权限/菜单并返回新 Profile */
    ProfileVO switchIdentity(IdentitySwitchDTO dto);

    /** 设置/修改密码 */
    void updatePassword(PasswordUpdateDTO dto);

    /** 敏感操作二次密码确认（开启 Sa-Token 二级认证） */
    ConfirmVO confirmPassword(PasswordConfirmDTO dto);

    /** 注销当前会话 */
    void logout();

    /** 用户角色码集合：持久化角色 + 有效助理授权动态计算的 ASSISTANT（含当前身份过滤） */
    List<String> loadRoleCodes(Long userId);

    /** 用户权限标识集合（持久化角色绑定；ASSISTANT 增量随物料模块落地；含当前身份过滤） */
    List<String> loadPermissionCodes(Long userId);

    /** 权限/名单/范围变更后踢掉会话强制重载（决策：变更踢会话） */
    void evictSession(Long userId);
}
