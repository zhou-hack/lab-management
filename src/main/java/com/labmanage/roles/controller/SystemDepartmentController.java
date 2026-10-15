package com.labmanage.roles.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.Result;
import com.labmanage.roles.service.SysUserService;
import com.labmanage.roles.vo.DepartmentOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理-组织部门（当前仅用户管理表单所需的下拉选项；完整 CRUD 随组织管理页补充）
 */
@Tag(name = "系统-组织部门")
@RestController
@RequestMapping("/api/v1/system/departments")
public class SystemDepartmentController {

    private final SysUserService sysUserService;

    public SystemDepartmentController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Operation(summary = "启用部门下拉选项（用户管理表单用）")
    @SaCheckPermission("system:user:list")
    @GetMapping("/options")
    public Result<List<DepartmentOptionVO>> options() {
        return Result.success(sysUserService.departmentOptions());
    }
}
