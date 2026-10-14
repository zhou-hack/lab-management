package com.labmanage.roles.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import com.labmanage.common.result.Result;
import com.labmanage.roles.auth.dto.LoginRequest;
import com.labmanage.roles.auth.service.AuthService;
import com.labmanage.roles.auth.vo.LoginVO;
import com.labmanage.roles.auth.vo.UserMenusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证（账号密码登录、注销、当前用户菜单）
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "账号密码登录（失败 5 次锁定 30 分钟）")
    @SaIgnore
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "注销当前会话（幂等）")
    @SaIgnore
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @Operation(summary = "当前用户 + 角色 + 权限 + 菜单树（M/C/F）")
    @SaCheckLogin
    @GetMapping("/user_menus")
    public Result<UserMenusVO> userMenus() {
        return Result.success(authService.userMenus());
    }
}
