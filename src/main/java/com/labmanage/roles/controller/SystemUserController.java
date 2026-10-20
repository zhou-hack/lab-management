package com.labmanage.roles.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.UserAdminRoleDTO;
import com.labmanage.roles.dto.UserCreateDTO;
import com.labmanage.roles.dto.UserEnabledDTO;
import com.labmanage.roles.dto.UserPageQueryDTO;
import com.labmanage.roles.dto.UserPasswordResetDTO;
import com.labmanage.roles.dto.UserUpdateDTO;
import com.labmanage.roles.service.SysUserService;
import com.labmanage.roles.vo.UserDetailVO;
import com.labmanage.roles.vo.UserPageItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理-用户管理（学生/老师账号由预录名单首登建档，不在此创建）
 */
@Tag(name = "系统-用户管理")
@RestController
@RequestMapping("/api/v1/system/users")
public class SystemUserController {

    private final SysUserService sysUserService;

    public SystemUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "用户分页列表")
    @SaCheckPermission("system:user:list")
    @GetMapping
    public Result<PageResult<UserPageItemVO>> page(@Valid UserPageQueryDTO query) {
        return Result.success(sysUserService.page(query));
    }

    @Operation(summary = "用户详情")
    @SaCheckPermission("system:user:get")
    @GetMapping("/{id}")
    public Result<UserDetailVO> detail(@PathVariable Long id) {
        return Result.success(sysUserService.detail(id));
    }

    @Operation(summary = "创建管理员账号（首登强制改密）")
    @SaCheckPermission("system:user:create")
    @OperationLog(moduleCode = "system", actionCode = "system:user:create", targetTable = "sys_user")
    @PostMapping
    public Result<UserDetailVO> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success(sysUserService.createAdmin(dto));
    }

    @Operation(summary = "编辑用户资料")
    @SaCheckPermission("system:user:update")
    @OperationLog(moduleCode = "system", actionCode = "system:user:update", targetTable = "sys_user", targetId = "#id")
    @PutMapping("/{id}")
    public Result<UserDetailVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        return Result.success(sysUserService.update(id, dto));
    }

    @Operation(summary = "启用/停用用户（停用踢出会话，启用解除锁定）")
    @SaCheckPermission("system:user:enable")
    @OperationLog(moduleCode = "system", actionCode = "system:user:enable", targetTable = "sys_user", targetId = "#id")
    @PutMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody UserEnabledDTO dto) {
        sysUserService.updateEnabled(id, dto);
        return Result.success();
    }

    @Operation(summary = "重置用户密码（强制首登改密并踢出会话；敏感操作需二级认证）")
    @SaCheckPermission("system:user:reset_password")
    @OperationLog(moduleCode = "system", actionCode = "system:user:reset_password", targetTable = "sys_user", targetId = "#id")
    @PutMapping("/{id}/password_reset")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody UserPasswordResetDTO dto) {
        StpUtil.checkSafe();
        sysUserService.resetPassword(id, dto);
        return Result.success();
    }

    @Operation(summary = "绑定/解绑老师账号的管理员角色（变更踢出会话）")
    @SaCheckPermission("system:user:bind_role")
    @OperationLog(moduleCode = "system", actionCode = "system:user:bind_role", targetTable = "sys_user_role", targetId = "#id")
    @PutMapping("/{id}/admin_role")
    public Result<Void> updateAdminRole(@PathVariable Long id, @Valid @RequestBody UserAdminRoleDTO dto) {
        sysUserService.updateAdminRole(id, dto);
        return Result.success();
    }
}
