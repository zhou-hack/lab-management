package com.labmanage.roles.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.roles.dto.AccountLoginDTO;
import com.labmanage.roles.dto.PasswordConfirmDTO;
import com.labmanage.roles.dto.PasswordUpdateDTO;
import com.labmanage.roles.dto.SmsLoginDTO;
import com.labmanage.roles.dto.SmsSendDTO;
import com.labmanage.roles.entity.SysMenu;
import com.labmanage.roles.entity.SysPersonRoster;
import com.labmanage.roles.entity.SysRole;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.entity.SysUserRole;
import com.labmanage.roles.enums.RoleCode;
import com.labmanage.roles.enums.UserType;
import com.labmanage.roles.mapper.AssistantGrantMapper;
import com.labmanage.roles.mapper.SysMenuMapper;
import com.labmanage.roles.mapper.SysPermissionMapper;
import com.labmanage.roles.mapper.SysPersonRosterMapper;
import com.labmanage.roles.mapper.SysRoleMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.mapper.SysUserRoleMapper;
import com.labmanage.roles.service.AuthService;
import com.labmanage.roles.service.PolicyService;
import com.labmanage.roles.service.SmsCodeService;
import com.labmanage.roles.vo.ConfirmVO;
import com.labmanage.roles.vo.LoginVO;
import com.labmanage.roles.vo.MenuVO;
import com.labmanage.roles.vo.ProfileVO;
import com.labmanage.roles.vo.SmsSendVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务实现。
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** 敏感操作二级认证有效期（秒） */
    private static final long SAFE_AUTH_SECONDS = 300;

    private final SysUserMapper userMapper;

    private final SysPersonRosterMapper rosterMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final SysRoleMapper roleMapper;

    private final SysPermissionMapper permissionMapper;

    private final SysMenuMapper menuMapper;

    private final AssistantGrantMapper grantMapper;

    private final SmsCodeService smsCodeService;

    private final PolicyService policyService;

    private final BCryptPasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserMapper userMapper, SysPersonRosterMapper rosterMapper,
                           SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper,
                           SysPermissionMapper permissionMapper, SysMenuMapper menuMapper,
                           AssistantGrantMapper grantMapper, SmsCodeService smsCodeService,
                           PolicyService policyService, BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.rosterMapper = rosterMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.grantMapper = grantMapper;
        this.smsCodeService = smsCodeService;
        this.policyService = policyService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginVO login(AccountLoginDTO dto) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getAccountName, dto.accountName()));
        if (user == null) {
            throw new BusinessException(40100, "账号或密码错误");
        }
        checkUsable(user);
        if (user.getPasswordHash() == null) {
            throw new BusinessException(40100, "该账号未设置密码，请使用验证码登录");
        }
        if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            onPasswordFail(user);
        }
        resetPasswordFail(user);
        return doLogin(user, false);
    }

    @Override
    public SmsSendVO sendSmsCode(SmsSendDTO dto) {
        return new SmsSendVO(smsCodeService.send(dto.phone()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO smsLogin(SmsLoginDTO dto) {
        smsCodeService.verify(dto.phone(), dto.code());
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getPhone, dto.phone()));
        if (user == null) {
            user = registerFromRoster(dto.phone());
        }
        checkUsable(user);
        boolean needSetPassword = user.getPasswordHash() == null
                && UserType.TEACHER.name().equals(user.getUserType());
        return doLogin(user, needSetPassword);
    }

    @Override
    public ProfileVO profile() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BusinessException(40100, "账号不存在或已停用");
        }
        List<String> roles = loadRoleCodes(userId);
        List<String> permissions = loadPermissionCodes(userId);
        StpUtil.getSession().set(SESSION_ROLES, roles);
        StpUtil.getSession().set(SESSION_PERMS, permissions);
        return new ProfileVO(String.valueOf(user.getId()), user.getRealName(), user.getUserType(),
                roles, permissions, buildMenuTree(menuMapper.selectMenusByUserId(userId)));
    }

    @Override
    public void updatePassword(PasswordUpdateDTO dto) {
        SysUser user = currentUser();
        if (user.getPasswordHash() != null) {
            if (dto.oldPassword() == null || !passwordEncoder.matches(dto.oldPassword(), user.getPasswordHash())) {
                throw new BusinessException(40100, "旧密码错误");
            }
        }
        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        userMapper.updateById(user);
    }

    @Override
    public ConfirmVO confirmPassword(PasswordConfirmDTO dto) {
        SysUser user = currentUser();
        if (user.getPasswordHash() == null) {
            throw new BusinessException(40100, "尚未设置密码，请先设置后再进行敏感操作确认");
        }
        if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            throw new BusinessException(40100, "密码错误");
        }
        StpUtil.openSafe(SAFE_AUTH_SECONDS);
        return new ConfirmVO((int) SAFE_AUTH_SECONDS);
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public List<String> loadRoleCodes(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getEnabled, 1));
        List<String> codes = new ArrayList<>();
        if (!userRoles.isEmpty()) {
            List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
            roleMapper.selectByIds(roleIds).stream()
                    .filter(role -> Integer.valueOf(1).equals(role.getEnabled()))
                    .map(SysRole::getRoleCode)
                    .forEach(codes::add);
        }
        if (grantMapper.countValidGrant(userId, Instant.now()) > 0) {
            codes.add(RoleCode.ASSISTANT.name());
        }
        return codes;
    }

    @Override
    public List<String> loadPermissionCodes(Long userId) {
        return permissionMapper.selectPermissionCodesByUserId(userId);
    }

    @Override
    public void evictSession(Long userId) {
        StpUtil.logout(userId);
    }

    /**
     * 首登建档：匹配有效预录名单，派生身份与主角色；客户端不得指定角色。
     */
    private SysUser registerFromRoster(String phone) {
        SysPersonRoster roster = rosterMapper.selectOne(Wrappers.<SysPersonRoster>lambdaQuery()
                .eq(SysPersonRoster::getPhone, phone));
        if (roster == null) {
            throw new BusinessException(40900, "该手机号未在预录名单中，无法登录，请联系管理员");
        }
        if (!Integer.valueOf(1).equals(roster.getEnabled())) {
            throw new BusinessException(40900, "预录名单已停用，请联系管理员");
        }
        SysUser user = new SysUser();
        user.setUserType(roster.getPersonType());
        user.setPhone(phone);
        user.setPersonNo(roster.getPersonNo());
        user.setRealName(roster.getRealName());
        user.setDepartmentId(roster.getDepartmentId());
        user.setEnabled(1);
        user.setFailedLoginCount(0);
        SysUserRole userRole = new SysUserRole();
        userRole.setEnabled(1);
        try {
            userMapper.insert(user);
            userRole.setUserId(user.getId());
            userRole.setRoleId(UserType.STUDENT.name().equals(roster.getPersonType())
                    ? RoleCode.STUDENT.getId() : RoleCode.TEACHER.getId());
            userRoleMapper.insert(userRole);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "账号已存在，请重新验证登录");
        }
        return user;
    }

    /**
     * 登录共通：校验可用性后签发会话，装配角色权限入会话缓存。
     */
    private LoginVO doLogin(SysUser user, boolean needSetPassword) {
        long timeout = UserType.ADMIN.name().equals(user.getUserType())
                ? policyService.getInt("SESSION_TIMEOUT_ADMIN", "timeoutSeconds", 28800)
                : policyService.getInt("SESSION_TIMEOUT_STUDENT_TEACHER", "timeoutSeconds", 7200);
        StpUtil.login(user.getId(), new SaLoginParameter().setTimeout(timeout));
        List<String> roles = loadRoleCodes(user.getId());
        List<String> permissions = loadPermissionCodes(user.getId());
        StpUtil.getSession().set(SESSION_ROLES, roles);
        StpUtil.getSession().set(SESSION_PERMS, permissions);
        return new LoginVO(StpUtil.getTokenValue(), String.valueOf(user.getId()), user.getRealName(),
                user.getUserType(), roles, passwordChangeRequired(user), needSetPassword);
    }

    private void checkUsable(SysUser user) {
        if (!Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BusinessException(40100, "账号已停用，请联系管理员");
        }
        Instant lockedUntil = user.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            long minutes = (Duration.between(Instant.now(), lockedUntil).toSeconds() + 59) / 60;
            throw new BusinessException(40100, "账号已锁定，请 " + minutes + " 分钟后重试");
        }
    }

    private void onPasswordFail(SysUser user) {
        int maxFail = policyService.getInt("LOGIN_MAX_FAIL", "maxFailCount", 5);
        int lockMinutes = policyService.getInt("LOGIN_LOCK_MINUTES", "lockMinutes", 30);
        int failCount = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
        if (failCount >= maxFail) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(Instant.now().plusSeconds(lockMinutes * 60L));
            userMapper.updateById(user);
            throw new BusinessException(40100, "连续失败 " + maxFail + " 次，账号已锁定 " + lockMinutes + " 分钟");
        }
        user.setFailedLoginCount(failCount);
        userMapper.updateById(user);
        throw new BusinessException(40100, "账号或密码错误，还可尝试 " + (maxFail - failCount) + " 次");
    }

    private void resetPasswordFail(SysUser user) {
        if ((user.getFailedLoginCount() != null && user.getFailedLoginCount() > 0) || user.getLockedUntil() != null) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            userMapper.updateById(user);
        }
    }

    private boolean passwordChangeRequired(SysUser user) {
        if (!UserType.ADMIN.name().equals(user.getUserType())) {
            return false;
        }
        int expireDays = policyService.getInt("PASSWORD_EXPIRE_DAYS", "expireDays", 90);
        Instant changedAt = user.getPasswordChangedAt();
        return changedAt == null || changedAt.plus(Duration.ofDays(expireDays)).isBefore(Instant.now());
    }

    private SysUser currentUser() {
        SysUser user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BusinessException(40100, "账号不存在或已停用");
        }
        return user;
    }

    private List<MenuVO> buildMenuTree(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> childrenByParent = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            childrenByParent.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
        }
        return toMenuVO(childrenByParent.get(null), childrenByParent);
    }

    private List<MenuVO> toMenuVO(List<SysMenu> menus, Map<Long, List<SysMenu>> childrenByParent) {
        if (menus == null) {
            return List.of();
        }
        return menus.stream().map(menu -> new MenuVO(
                String.valueOf(menu.getId()),
                menu.getParentId() == null ? null : String.valueOf(menu.getParentId()),
                menu.getMenuCode(), menu.getMenuName(), menu.getRoutePath(), menu.getComponentKey(),
                menu.getMenuType(), menu.getSortOrder(),
                toMenuVO(childrenByParent.get(menu.getId()), childrenByParent)
        )).toList();
    }
}
