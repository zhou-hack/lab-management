package com.labmanage.roles.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.ScopeCreateDTO;
import com.labmanage.roles.dto.ScopePageQueryDTO;
import com.labmanage.roles.service.SysScopeService;
import com.labmanage.roles.vo.AdminOptionVO;
import com.labmanage.roles.vo.ScopePageItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理-数据范围（管理员归口授权；无范围记录不自动授予全校数据）
 */
@Tag(name = "系统-数据范围")
@RestController
@RequestMapping("/api/v1/system/scopes")
public class SystemScopeController {

    private final SysScopeService scopeService;

    public SystemScopeController(SysScopeService scopeService) {
        this.scopeService = scopeService;
    }

    @Operation(summary = "范围授权分页列表")
    @RequirePermission("system:scope:list")
    @GetMapping
    public Result<PageResult<ScopePageItemVO>> page(@Valid ScopePageQueryDTO query) {
        return Result.success(scopeService.page(query));
    }

    @Operation(summary = "可授权管理员下拉（启用中的 ADMIN）")
    @RequirePermission("system:scope:grant")
    @GetMapping("/admin_options")
    public Result<List<AdminOptionVO>> adminOptions(
            @RequestParam(required = false) @Size(max = 32, message = "关键字长度不能超过 32") String keyword) {
        return Result.success(scopeService.adminOptions(keyword));
    }

    @Operation(summary = "新增范围授权（同目标升降级通过增删行实现；敏感操作需二级认证）")
    @RequirePermission("system:scope:grant")
    @OperationLog(moduleCode = "system", actionCode = "system:scope:grant", targetTable = "sys_user_data_scope")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ScopeCreateDTO dto) {
        SecurityUtils.checkSafe();
        scopeService.create(dto);
        return Result.success();
    }

    @Operation(summary = "删除范围授权（物理删除，可重新授权；敏感操作需二级认证）")
    @RequirePermission("system:scope:grant")
    @OperationLog(moduleCode = "system", actionCode = "system:scope:revoke", targetTable = "sys_user_data_scope", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        SecurityUtils.checkSafe();
        scopeService.remove(id);
        return Result.success();
    }
}
