package com.labmanage.roles.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.RolePermissionAssignDTO;
import com.labmanage.roles.service.SysRoleService;
import com.labmanage.roles.vo.PermissionOptionVO;
import com.labmanage.roles.vo.RoleDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理-角色权限（只读视图 + 运维管理员整集替换权限绑定）
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
    @RequirePermission("system:role:list")
    @GetMapping
    public Result<List<RoleDetailVO>> list() {
        return Result.success(roleService.listDetail());
    }

    @Operation(summary = "全部启用权限（角色权限编辑选项）")
    @RequirePermission("system:role:assign")
    @GetMapping("/permissions")
    public Result<List<PermissionOptionVO>> permissionOptions() {
        return Result.success(roleService.listPermissionOptions());
    }

    @Operation(summary = "整集替换角色权限绑定（OPS_ADMIN 锁定；踢出该角色持有者会话；敏感操作需二级认证）")
    @RequirePermission("system:role:assign")
    @OperationLog(moduleCode = "system", actionCode = "system:role:assign", targetTable = "sys_role_permission", targetId = "#id")
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody RolePermissionAssignDTO dto) {
        SecurityUtils.checkSafe();
        roleService.assignPermissions(id, dto);
        return Result.success();
    }
}
