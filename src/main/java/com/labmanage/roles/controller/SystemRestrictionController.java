package com.labmanage.roles.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.RestrictionCreateDTO;
import com.labmanage.roles.dto.RestrictionPageQueryDTO;
import com.labmanage.roles.service.SysRestrictionService;
import com.labmanage.roles.vo.RestrictionPageItemVO;
import com.labmanage.roles.vo.UserOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统管理-预约限制（时间内禁止指定 EQUIPMENT/LAB/ALL 预约；自动逾期冻结由单据模块接入）
 */
@Tag(name = "系统-预约限制")
@RestController
@RequestMapping("/api/v1/system/restrictions")
public class SystemRestrictionController {

    private final SysRestrictionService restrictionService;

    public SystemRestrictionController(SysRestrictionService restrictionService) {
        this.restrictionService = restrictionService;
    }

    @Operation(summary = "预约限制分页列表")
    @RequirePermission("system:restriction:list")
    @GetMapping
    public Result<PageResult<RestrictionPageItemVO>> page(@Valid RestrictionPageQueryDTO query) {
        return Result.success(restrictionService.page(query));
    }

    @Operation(summary = "被限制用户下拉（启用中全部类型）")
    @RequirePermission("system:restriction:create")
    @GetMapping("/user_options")
    public Result<List<UserOptionVO>> userOptions(
            @RequestParam(required = false) @Size(max = 32, message = "关键字长度不能超过 32") String keyword) {
        return Result.success(restrictionService.userOptions(keyword));
    }

    @Operation(summary = "手工新增预约限制")
    @RequirePermission("system:restriction:create")
    @OperationLog(moduleCode = "system", actionCode = "system:restriction:create", targetTable = "user_booking_restriction")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody RestrictionCreateDTO dto) {
        restrictionService.create(dto);
        return Result.success();
    }

    @Operation(summary = "撤销预约限制")
    @RequirePermission("system:restriction:revoke")
    @OperationLog(moduleCode = "system", actionCode = "system:restriction:revoke", targetTable = "user_booking_restriction", targetId = "#id")
    @PutMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        restrictionService.revoke(id);
        return Result.success();
    }
}
