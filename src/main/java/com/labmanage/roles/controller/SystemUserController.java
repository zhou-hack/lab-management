package com.labmanage.roles.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.UserCreateDTO;
import com.labmanage.roles.dto.UserEnabledDTO;
import com.labmanage.roles.dto.UserPageQueryDTO;
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
    @PostMapping
    public Result<UserDetailVO> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success(sysUserService.createAdmin(dto));
    }

    @Operation(summary = "编辑用户资料")
    @SaCheckPermission("system:user:update")
    @PutMapping("/{id}")
    public Result<UserDetailVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        return Result.success(sysUserService.update(id, dto));
    }

    @Operation(summary = "启用/停用用户（停用踢出会话，启用解除锁定）")
    @SaCheckPermission("system:user:enable")
    @PutMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody UserEnabledDTO dto) {
        sysUserService.updateEnabled(id, dto);
        return Result.success();
    }
}
