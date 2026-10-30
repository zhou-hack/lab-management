package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.roles.dto.RolePermissionAssignDTO;
import com.labmanage.roles.entity.SysMenu;
import com.labmanage.roles.entity.SysPermission;
import com.labmanage.roles.entity.SysRole;
import com.labmanage.roles.entity.SysRoleMenu;
import com.labmanage.roles.entity.SysRolePermission;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.entity.SysUserRole;
import com.labmanage.roles.enums.RoleCode;
import com.labmanage.roles.mapper.AssistantGrantMapper;
import com.labmanage.roles.mapper.SysDepartmentMapper;
import com.labmanage.roles.mapper.SysMenuMapper;
import com.labmanage.roles.mapper.SysPermissionMapper;
import com.labmanage.roles.mapper.SysRoleMapper;
import com.labmanage.roles.mapper.SysRoleMenuMapper;
import com.labmanage.roles.mapper.SysRolePermissionMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.mapper.SysUserRoleMapper;
import com.labmanage.roles.service.AuthService;
import com.labmanage.roles.service.SysRoleService;
import com.labmanage.roles.vo.PermissionOptionVO;
import com.labmanage.roles.vo.RoleDetailVO;
import com.labmanage.roles.vo.RoleUserItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 角色只读视图服务实现（数据量小，内存组装）
 */
@Service
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;

    private final SysRolePermissionMapper rolePermissionMapper;

    private final SysRoleMenuMapper roleMenuMapper;

    private final SysPermissionMapper permissionMapper;

    private final SysMenuMapper menuMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final AssistantGrantMapper grantMapper;

    private final SysUserMapper userMapper;

    private final SysDepartmentMapper departmentMapper;

    private final AuthService authService;

    public SysRoleServiceImpl(SysRoleMapper roleMapper, SysRolePermissionMapper rolePermissionMapper,
                              SysRoleMenuMapper roleMenuMapper, SysPermissionMapper permissionMapper,
                              SysMenuMapper menuMapper, SysUserRoleMapper userRoleMapper,
                              AssistantGrantMapper grantMapper, SysUserMapper userMapper,
                              SysDepartmentMapper departmentMapper, AuthService authService) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.userRoleMapper = userRoleMapper;
        this.grantMapper = grantMapper;
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.authService = authService;
    }

    @Override
    public List<RoleDetailVO> listDetail() {
        List<SysRole> roles = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .orderByAsc(SysRole::getId));
        Map<Long, SysPermission> permissions = permissionMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysPermission::getId, Function.identity()));
        Map<Long, SysMenu> menus = menuMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysMenu::getId, Function.identity()));

        Map<Long, List<SysRolePermission>> permsByRole = rolePermissionMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(SysRolePermission::getRoleId));
        Map<Long, List<SysRoleMenu>> menusByRole = roleMenuMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(SysRoleMenu::getRoleId));
        Map<Long, Long> userCountByRole = new HashMap<>();
        for (SysUserRole binding : userRoleMapper.selectList(null)) {
            userCountByRole.merge(binding.getRoleId(), 1L, Long::sum);
        }

        List<RoleDetailVO> result = new ArrayList<>();
        for (SysRole role : roles) {
            List<RoleDetailVO.PermissionItem> permItems = new ArrayList<>();
            for (SysRolePermission binding : permsByRole.getOrDefault(role.getId(), List.of())) {
                SysPermission permission = permissions.get(binding.getPermissionId());
                if (permission != null) {
                    permItems.add(new RoleDetailVO.PermissionItem(
                            String.valueOf(permission.getId()), permission.getPermissionCode(),
                            permission.getPermissionName(), permission.getModuleCode()));
                }
            }
            permItems.sort(Comparator.comparing(RoleDetailVO.PermissionItem::permissionCode));

            List<RoleDetailVO.MenuItem> menuItems = new ArrayList<>();
            for (SysRoleMenu binding : menusByRole.getOrDefault(role.getId(), List.of())) {
                SysMenu menu = menus.get(binding.getMenuId());
                if (menu != null) {
                    menuItems.add(new RoleDetailVO.MenuItem(
                            menu.getMenuName(), menu.getMenuType(), menu.getRoutePath()));
                }
            }
            menuItems.sort(Comparator.comparing(RoleDetailVO.MenuItem::menuName));

            result.add(new RoleDetailVO(
                    String.valueOf(role.getId()),
                    role.getRoleCode(),
                    role.getRoleName(),
                    role.getEnabled(),
                    userCountByRole.getOrDefault(role.getId(), 0L),
                    permItems,
                    menuItems));
        }
        return result;
    }

    @Override
    public List<PermissionOptionVO> listPermissionOptions() {
        return permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                        .eq(SysPermission::getEnabled, 1)
                        .orderByAsc(SysPermission::getId)).stream()
                .map(p -> new PermissionOptionVO(String.valueOf(p.getId()), p.getPermissionCode(),
                        p.getPermissionName(), p.getModuleCode()))
                .toList();
    }

    @Override
    public List<RoleUserItemVO> listRoleUsers(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(40400, "角色不存在");
        }
        // ASSISTANT 不持久化绑定，由有效助理授权动态计算；其余角色读 sys_user_role
        Set<Long> userIds;
        if (RoleCode.ASSISTANT.name().equals(role.getRoleCode())) {
            userIds = new HashSet<>(grantMapper.selectValidStudentIds(Instant.now()));
        } else {
            userIds = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                            .eq(SysUserRole::getRoleId, roleId)).stream()
                    .map(SysUserRole::getUserId).collect(Collectors.toSet());
        }
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getId, userIds)
                .orderByAsc(SysUser::getId));
        Map<Long, String> departmentNames = departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(d -> d.getId(), d -> d.getDepartmentName()));
        return users.stream()
                .map(u -> new RoleUserItemVO(
                        String.valueOf(u.getId()),
                        u.getUserType(),
                        u.getRealName(),
                        u.getAccountName(),
                        u.getPersonNo(),
                        u.getDepartmentId() == null ? null : departmentNames.get(u.getDepartmentId()),
                        u.getEnabled()))
                .toList();
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, RolePermissionAssignDTO dto) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(40400, "角色不存在");
        }
        if (RoleCode.OPS_ADMIN.name().equals(role.getRoleCode())) {
            throw new BusinessException(40900, "运维管理员角色绑定全部权限，不可调整");
        }
        List<Long> targetIds = dto.permissionIds().stream().distinct().toList();
        Set<Long> enabledIds = permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                        .eq(SysPermission::getEnabled, 1)).stream()
                .map(SysPermission::getId).collect(Collectors.toSet());
        for (Long permissionId : targetIds) {
            if (!enabledIds.contains(permissionId)) {
                throw new BusinessException(40000, "包含无效或停用的权限：" + permissionId);
            }
        }
        Set<Long> currentIds = rolePermissionMapper.selectList(Wrappers.<SysRolePermission>lambdaQuery()
                        .eq(SysRolePermission::getRoleId, roleId)).stream()
                .map(SysRolePermission::getPermissionId).collect(Collectors.toSet());
        Set<Long> targetSet = new HashSet<>(targetIds);
        List<Long> toRemove = currentIds.stream().filter(pid -> !targetSet.contains(pid)).toList();
        List<Long> toAdd = targetIds.stream().filter(pid -> !currentIds.contains(pid)).toList();
        if (toRemove.isEmpty() && toAdd.isEmpty()) {
            return;
        }
        if (!toRemove.isEmpty()) {
            // uk(role_id, permission_id) 不含 deleted：移除项物理删除，避免重新绑定撞唯一键
            rolePermissionMapper.physicalDeleteByRoleAndPermissions(roleId, toRemove);
        }
        for (Long permissionId : toAdd) {
            SysRolePermission binding = new SysRolePermission();
            binding.setRoleId(roleId);
            binding.setPermissionId(permissionId);
            binding.setEnabled(1);
            rolePermissionMapper.insert(binding);
        }
        evictRoleHolders(role);
    }

    /** 权限为会话缓存：踢出该角色全部持有者（ASSISTANT 含有效授权动态持有者），重新登录生效 */
    private void evictRoleHolders(SysRole role) {
        Set<Long> userIds = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getRoleId, role.getId())).stream()
                .map(SysUserRole::getUserId).collect(Collectors.toSet());
        if (RoleCode.ASSISTANT.name().equals(role.getRoleCode())) {
            userIds.addAll(grantMapper.selectValidStudentIds(Instant.now()));
        }
        userIds.forEach(authService::evictSession);
    }
}
