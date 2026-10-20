package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.security.JwtService;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.roles.dto.AccountLoginDTO;
import com.labmanage.roles.dto.PasswordConfirmDTO;
import com.labmanage.roles.dto.RefreshTokenDTO;
import com.labmanage.roles.entity.SysMenu;
import com.labmanage.roles.entity.SysRole;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.entity.SysUserRole;
import com.labmanage.roles.enums.RoleCode;
import com.labmanage.roles.mapper.AssistantGrantMapper;
import com.labmanage.roles.mapper.SysMenuMapper;
import com.labmanage.roles.mapper.SysPermissionMapper;
import com.labmanage.roles.mapper.SysRoleMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.mapper.SysUserRoleMapper;
import com.labmanage.roles.service.AuthService;
import com.labmanage.roles.service.PolicyService;
import com.labmanage.roles.vo.ConfirmVO;
import com.labmanage.roles.vo.LoginVO;
import com.labmanage.roles.vo.MenuVO;
import com.labmanage.roles.vo.ProfileVO;
import com.labmanage.roles.vo.SaltVO;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务实现（JWT 无状态会话）。
 * <p>
 * 口令链路：客户端按账号预取盐 → 本地 bcrypt 算出与库中一致的哈希 → 上传，
 * 服务端常量时间比对，全程无明文传输（内网/开发场景接受哈希即凭据的重放风险）。
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** BCrypt 哈希的盐前缀长度（$2a$10$ + 22 字符盐） */
    private static final int BCRYPT_SALT_PREFIX_LENGTH = 29;

    private final SysUserMapper userMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final SysRoleMapper roleMapper;

    private final SysPermissionMapper permissionMapper;

    private final SysMenuMapper menuMapper;

    private final AssistantGrantMapper grantMapper;

    private final PolicyService policyService;

    private final JwtService jwtService;

    public AuthServiceImpl(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                           SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
                           SysMenuMapper menuMapper, AssistantGrantMapper grantMapper,
                           PolicyService policyService, JwtService jwtService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.grantMapper = grantMapper;
        this.policyService = policyService;
        this.jwtService = jwtService;
    }

    @Override
    public SaltVO salt(String accountName) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getAccountName, accountName));
        // 账号不存在与未设密码返回同一错误，不泄露账号是否存在
        if (user == null || user.getPasswordHash() == null
                || user.getPasswordHash().length() < BCRYPT_SALT_PREFIX_LENGTH) {
            throw new BusinessException(40100, "账号或密码错误");
        }
        return new SaltVO(user.getPasswordHash().substring(0, BCRYPT_SALT_PREFIX_LENGTH));
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
            throw new BusinessException(40100, "该账号未设置密码，请联系管理员重置");
        }
        if (!hashEquals(dto.password(), user.getPasswordHash())) {
            onPasswordFail(user);
        }
        resetPasswordFail(user);
        return issueTokens(user);
    }

    @Override
    public LoginVO refresh(RefreshTokenDTO dto) {
        Long userId = jwtService.parseUserId(dto.refreshToken(), JwtService.TYPE_REFRESH);
        if (userId == null) {
            throw new BusinessException(40100, "refresh token 无效或已过期");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BusinessException(40100, "账号不存在或已停用");
        }
        return issueTokens(user);
    }

    @Override
    public ProfileVO profile() {
        Long userId = SecurityUtils.currentUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BusinessException(40100, "账号不存在或已停用");
        }
        return new ProfileVO(String.valueOf(user.getId()), user.getRealName(), user.getUserType(),
                loadRoleCodes(userId), loadPermissionCodes(userId),
                buildMenuTree(menuMapper.selectMenusByUserId(userId)));
    }

    @Override
    public ConfirmVO confirmPassword(PasswordConfirmDTO dto) {
        SysUser user = currentUser();
        if (user.getPasswordHash() == null) {
            throw new BusinessException(40100, "尚未设置密码，请联系管理员重置");
        }
        if (!hashEquals(dto.password(), user.getPasswordHash())) {
            throw new BusinessException(40100, "密码错误");
        }
        SecurityUtils.openSafe();
        return new ConfirmVO((int) SecurityUtils.SAFE_AUTH_SECONDS);
    }

    @Override
    public void logout() {
        // 无状态 JWT：服务端不持有会话，客户端丢弃 token 即完成注销
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
        // 无状态 JWT 无法主动踢会话；角色权限每请求实时加载，access token 最长 2h 自然过期
    }

    /** 签发 access+refresh token 并装配响应 */
    private LoginVO issueTokens(SysUser user) {
        return new LoginVO(jwtService.issueAccess(user.getId()), jwtService.issueRefresh(user.getId()),
                jwtService.getAccessTtlSeconds(), String.valueOf(user.getId()), user.getRealName(),
                user.getUserType(), loadRoleCodes(user.getId()));
    }

    /** 常量时间比对客户端 bcrypt 密文与库中哈希（防时序侧信道） */
    private boolean hashEquals(String clientHash, String storedHash) {
        if (clientHash == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                clientHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
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

    private SysUser currentUser() {
        SysUser user = userMapper.selectById(SecurityUtils.currentUserId());
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
