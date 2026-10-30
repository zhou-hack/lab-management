package com.labmanage.roles.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.DepartmentCreateDTO;
import com.labmanage.roles.dto.DepartmentEnabledDTO;
import com.labmanage.roles.dto.DepartmentUpdateDTO;
import com.labmanage.roles.service.SysDepartmentService;
import com.labmanage.roles.service.SysUserService;
import com.labmanage.roles.vo.DepartmentOptionVO;
import com.labmanage.roles.vo.DepartmentTreeVO;
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

import java.util.List;

/**
 * 系统管理-组织部门
 */
@Tag(name = "系统-组织部门")
@RestController
@RequestMapping("/api/v1/system/departments")
public class SystemDepartmentController {

    private final SysUserService sysUserService;

    private final SysDepartmentService departmentService;

    public SystemDepartmentController(SysUserService sysUserService, SysDepartmentService departmentService) {
        this.sysUserService = sysUserService;
        this.departmentService = departmentService;
    }

    // 登录即可用：业务表单（设备台账归属部门等）与系统页共用的只读下拉，不挂 system 权限
    @Operation(summary = "启用部门下拉选项（业务/系统表单共用）")
    @GetMapping("/options")
    public Result<List<DepartmentOptionVO>> options() {
        return Result.success(sysUserService.departmentOptions());
    }

    @Operation(summary = "部门树（含停用节点）")
    @RequirePermission("system:department:list")
    @GetMapping
    public Result<List<DepartmentTreeVO>> tree() {
        return Result.success(departmentService.tree());
    }

    @Operation(summary = "新增部门")
    @RequirePermission("system:department:create")
    @OperationLog(moduleCode = "system", actionCode = "system:department:create", targetTable = "sys_department")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody DepartmentCreateDTO dto) {
        departmentService.create(dto);
        return Result.success();
    }

    @Operation(summary = "编辑部门（编码不可改，父部门禁止循环）")
    @RequirePermission("system:department:update")
    @OperationLog(moduleCode = "system", actionCode = "system:department:update", targetTable = "sys_department", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateDTO dto) {
        departmentService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用部门（有启用子部门或被引用时禁止停用）")
    @RequirePermission("system:department:enable")
    @OperationLog(moduleCode = "system", actionCode = "system:department:enable", targetTable = "sys_department", targetId = "#id")
    @PutMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody DepartmentEnabledDTO dto) {
        departmentService.updateEnabled(id, dto);
        return Result.success();
    }
}
