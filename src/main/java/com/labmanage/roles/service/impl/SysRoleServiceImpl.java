package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.roles.entity.SysMenu;
import com.labmanage.roles.entity.SysPermission;
import com.labmanage.roles.entity.SysRole;
import com.labmanage.roles.entity.SysRoleMenu;
import com.labmanage.roles.entity.SysRolePermission;
import com.labmanage.roles.entity.SysUserRole;
import com.labmanage.roles.mapper.SysMenuMapper;
import com.labmanage.roles.mapper.SysPermissionMapper;
import com.labmanage.roles.mapper.SysRoleMapper;
import com.labmanage.roles.mapper.SysRoleMenuMapper;
import com.labmanage.roles.mapper.SysRolePermissionMapper;
import com.labmanage.roles.mapper.SysUserRoleMapper;
import com.labmanage.roles.service.SysRoleService;
import com.labmanage.roles.vo.RoleDetailVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public SysRoleServiceImpl(SysRoleMapper roleMapper, SysRolePermissionMapper rolePermissionMapper,
                              SysRoleMenuMapper roleMenuMapper, SysPermissionMapper permissionMapper,
                              SysMenuMapper menuMapper, SysUserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.userRoleMapper = userRoleMapper;
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
                            permission.getPermissionCode(), permission.getPermissionName(),
                            permission.getModuleCode()));
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
}
