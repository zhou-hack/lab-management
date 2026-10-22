package com.labmanage.roles.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.AccountLoginDTO;
import com.labmanage.roles.dto.PasswordConfirmDTO;
import com.labmanage.roles.dto.PasswordUpdateDTO;
import com.labmanage.roles.dto.SmsLoginDTO;
import com.labmanage.roles.dto.SmsSendDTO;
import com.labmanage.roles.dto.SwitchRoleDTO;
import com.labmanage.roles.service.AuthService;
import com.labmanage.roles.vo.ConfirmVO;
import com.labmanage.roles.vo.LoginVO;
import com.labmanage.roles.vo.ProfileVO;
import com.labmanage.roles.vo.SmsSendVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（契约见 docs/RBAC实施清单.md 第四节）
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "账号密码登录（管理员）")
    @OperationLog(moduleCode = "auth", actionCode = "auth:login", targetTable = "sys_user",
            targetId = "#result?.data?.userId")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody AccountLoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "发送短信验证码（学生/老师）")
    @PostMapping("/sms/send")
    public Result<SmsSendVO> sendSmsCode(@Valid @RequestBody SmsSendDTO dto) {
        return Result.success(authService.sendSmsCode(dto));
    }

    @Operation(summary = "短信验证码登录（首登自动建档）")
    @OperationLog(moduleCode = "auth", actionCode = "auth:sms_login", targetTable = "sys_user",
            targetId = "#result?.data?.userId")
    @PostMapping("/sms/login")
    public Result<LoginVO> smsLogin(@Valid @RequestBody SmsLoginDTO dto) {
        return Result.success(authService.smsLogin(dto));
    }

    @Operation(summary = "当前身份（角色/权限/菜单）")
    @SaCheckLogin
    @GetMapping("/profile")
    public Result<ProfileVO> profile() {
        return Result.success(authService.profile());
    }

    @Operation(summary = "切换活动角色（多角色用户会话内切换身份视图）")
    @OperationLog(moduleCode = "auth", actionCode = "auth:switch_role", targetTable = "sys_user")
    @SaCheckLogin
    @PostMapping("/switch_role")
    public Result<ProfileVO> switchRole(@Valid @RequestBody SwitchRoleDTO dto) {
        return Result.success(authService.switchRole(dto));
    }

    @Operation(summary = "设置/修改密码")
    @SaCheckLogin
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody PasswordUpdateDTO dto) {
        authService.updatePassword(dto);
        return Result.success();
    }

    @Operation(summary = "敏感操作二次密码确认")
    @SaCheckLogin
    @PostMapping("/password/confirm")
    public Result<ConfirmVO> confirmPassword(@Valid @RequestBody PasswordConfirmDTO dto) {
        return Result.success(authService.confirmPassword(dto));
    }

    @Operation(summary = "注销")
    @OperationLog(moduleCode = "auth", actionCode = "auth:logout", targetTable = "sys_user")
    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }
}
