package com.labmanage.roles.controller;

import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.AccountLoginDTO;
import com.labmanage.roles.dto.PasswordConfirmDTO;
import com.labmanage.roles.dto.RefreshTokenDTO;
import com.labmanage.roles.dto.SwitchRoleDTO;
import com.labmanage.roles.service.AuthService;
import com.labmanage.roles.vo.ConfirmVO;
import com.labmanage.roles.vo.LoginVO;
import com.labmanage.roles.vo.ProfileVO;
import com.labmanage.roles.vo.SaltVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：取盐、账号密码登录、token 刷新、身份、活动角色切换、二级确认、注销。
 * 口令全程为客户端 bcrypt 密文，不传输明文（公开端点由 JwtAuthFilter 放行）。
 */
@Tag(name = "认证")
@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "按账号预取 BCrypt 盐（登录/二级确认前获取）")
    @GetMapping("/salt")
    public Result<SaltVO> salt(@RequestParam @NotBlank(message = "账号不能为空")
                               @Size(max = 64, message = "账号长度不能超过 64") String accountName) {
        return Result.success(authService.salt(accountName));
    }

    @Operation(summary = "账号密码登录（password 为客户端 bcrypt 密文）")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody AccountLoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "refresh token 换新（签发新 token 对，保留活动角色）")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return Result.success(authService.refresh(dto));
    }

    @Operation(summary = "当前身份（按活动角色装配角色/权限/菜单）")
    @GetMapping("/profile")
    public Result<ProfileVO> profile() {
        return Result.success(authService.profile());
    }

    @Operation(summary = "切换活动角色（多角色用户切换身份视图，重签 token 对）")
    @PostMapping("/switch_role")
    public Result<LoginVO> switchRole(@Valid @RequestBody SwitchRoleDTO dto) {
        return Result.success(authService.switchRole(dto));
    }

    @Operation(summary = "敏感操作二次密码确认（password 为客户端 bcrypt 密文）")
    @PostMapping("/password/confirm")
    public Result<ConfirmVO> confirmPassword(@Valid @RequestBody PasswordConfirmDTO dto) {
        return Result.success(authService.confirmPassword(dto));
    }

    @Operation(summary = "注销（无状态：客户端丢弃 token 即可）")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }
}
