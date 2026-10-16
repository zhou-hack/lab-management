package com.labmanage.roles.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.Result;
import com.labmanage.roles.service.SysRoleService;
import com.labmanage.roles.vo.RoleDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理-角色权限（只读视图；角色与权限绑定由 SQL 种子维护）
 */
@Tag(name = "系统-角色权限")
@RestController
@RequestMapping("/api/v1/system/roles")
public class SystemRoleController {

    private final SysRoleService roleService;

    public SystemRoleController(SysRoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "角色权限只读列表（角色 + 权限 + 菜单 + 绑定用户数）")
    @SaCheckPermission("system:role:list")
    @GetMapping
    public Result<List<RoleDetailVO>> list() {
        return Result.success(roleService.listDetail());
    }
}
