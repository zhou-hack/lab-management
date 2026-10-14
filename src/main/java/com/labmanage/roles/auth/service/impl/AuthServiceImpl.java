package com.labmanage.roles.auth.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.roles.auth.dto.LoginRequest;
import com.labmanage.roles.auth.service.AuthService;
import com.labmanage.roles.auth.vo.LoginVO;
import com.labmanage.roles.auth.vo.MenuNodeVO;
import com.labmanage.roles.auth.vo.UserInfoVO;
import com.labmanage.roles.auth.vo.UserMenusVO;
import com.labmanage.roles.menu.entity.SysMenu;
import com.labmanage.roles.menu.mapper.SysMenuMapper;
import com.labmanage.roles.user.entity.SysUser;
import com.labmanage.roles.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证服务实现
 * <p>
 * 失败计数：连续失败达 5 次锁定 30 分钟，锁定释放后重新计数；
 * 密码散列由 sql 种子预置（BCrypt），首登改密策略由后续安全模块补充。
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** 连续失败达该次数锁定账号 */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /** 锁定时长（分钟） */
    private static final int LOCK_MINUTES = 30;

    /** 树构建哨兵：根节点分组键（parentId 为 NULL 或父不可见时归入） */
    private static final Long ROOT_KEY = -1L;

    private final SysUserMapper sysUserMapper;
    private final SysMenuMapper sysMenuMapper;

    public AuthServiceImpl(SysUserMapper sysUserMapper, SysMenuMapper sysMenuMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    @Override
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccountName, request.username()));

        // 锁定优先于密码校验，锁定期内拒绝
        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "失败次数过多，账号已锁定 30 分钟");
        }

        boolean passwordOk = user != null
                && user.getPasswordHash() != null
                && BCrypt.checkpw(request.password(), user.getPasswordHash());
        if (!passwordOk) {
            // 统一提示，不区分账号不存在与密码错误
            if (user != null) {
                recordLoginFailure(user);
            }
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "账号或密码错误");
        }

        if (user.getEnabled() == null || user.getEnabled() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "账号已停用");
        }

        resetLoginFailure(user);
        StpUtil.login(user.getId());
        return new LoginVO(StpUtil.getTokenValue());
    }

    @Override
    public void logout() {
        // 幂等：未登录时注销同样视为成功
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
    }

    @Override
    public UserMenusVO userMenus() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            // 会话有效但账号已删除/停用：按未认证处理，前端将清理状态回登录页
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        UserInfoVO userInfo = new UserInfoVO(
                user.getId().toString(),
                user.getRealName(),
                user.getAccountName(),
                user.getUserType()
        );
        List<String> roles = sysUserMapper.selectRoleCodes(userId);
        List<String> perms = sysUserMapper.selectPermissionCodes(userId);
        List<MenuNodeVO> menus = buildMenuTree(sysMenuMapper.selectUserMenus(userId));
        return new UserMenusVO(userInfo, roles, perms, menus);
    }

    /**
     * 登录失败计数；达上限锁定并重新计数
     */
    private void recordLoginFailure(SysUser user) {
        int failed = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId());
        if (failed >= MAX_FAILED_ATTEMPTS) {
            wrapper.set(SysUser::getFailedLoginCount, 0)
                    .set(SysUser::getLockedUntil, Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
        } else {
            wrapper.set(SysUser::getFailedLoginCount, failed);
        }
        sysUserMapper.update(null, wrapper);
    }

    /**
     * 登录成功后清零失败计数与锁定残留
     */
    private void resetLoginFailure(SysUser user) {
        boolean hasFailure = (user.getFailedLoginCount() != null && user.getFailedLoginCount() > 0)
                || user.getLockedUntil() != null;
        if (hasFailure) {
            sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, user.getId())
                    .set(SysUser::getFailedLoginCount, 0)
                    .set(SysUser::getLockedUntil, null));
        }
    }

    /**
     * 构建菜单树：父节点缺失（或不在可见集合内）的节点提升为根，防止菜单丢失
     */
    private List<MenuNodeVO> buildMenuTree(List<SysMenu> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> allIds = rows.stream().map(SysMenu::getId).collect(Collectors.toSet());
        Map<Long, List<SysMenu>> byParent = rows.stream().collect(Collectors.groupingBy(
                menu -> menu.getParentId() != null && allIds.contains(menu.getParentId())
                        ? menu.getParentId()
                        : ROOT_KEY
        ));
        return buildChildren(byParent, ROOT_KEY);
    }

    private List<MenuNodeVO> buildChildren(Map<Long, List<SysMenu>> byParent, Long parentId) {
        List<SysMenu> children = byParent.getOrDefault(parentId, List.of());
        List<MenuNodeVO> nodes = new ArrayList<>(children.size());
        for (SysMenu menu : children) {
            nodes.add(new MenuNodeVO(
                    menu.getId().toString(),
                    menu.getMenuName(),
                    mapMenuType(menu.getMenuType()),
                    menu.getRoutePath(),
                    menu.getPermissionCode(),
                    buildChildren(byParent, menu.getId())
            ));
        }
        return nodes;
    }

    /**
     * DB 菜单类型 → 前端契约类型
     */
    private String mapMenuType(String menuType) {
        return switch (menuType) {
            case "DIRECTORY" -> "M";
            case "PAGE" -> "C";
            case "ACTION" -> "F";
            default -> throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(),
                    "未知菜单类型: " + menuType);
        };
    }
}
