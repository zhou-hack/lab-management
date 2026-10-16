package com.labmanage.roles.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.roles.dto.RosterBatchCreateDTO;
import com.labmanage.roles.dto.RosterCreateDTO;
import com.labmanage.roles.dto.RosterEnabledDTO;
import com.labmanage.roles.dto.RosterPageQueryDTO;
import com.labmanage.roles.dto.RosterUpdateDTO;
import com.labmanage.roles.service.SysRosterService;
import com.labmanage.roles.vo.RosterPageItemVO;
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
 * 系统管理-名单管理（学生/老师账号的唯一建档来源）
 */
@Tag(name = "系统-名单管理")
@RestController
@RequestMapping("/api/v1/system/rosters")
public class SystemRosterController {

    private final SysRosterService rosterService;

    public SystemRosterController(SysRosterService rosterService) {
        this.rosterService = rosterService;
    }

    @Operation(summary = "名单分页列表")
    @SaCheckPermission("system:roster:list")
    @GetMapping
    public Result<PageResult<RosterPageItemVO>> page(@Valid RosterPageQueryDTO query) {
        return Result.success(rosterService.page(query));
    }

    @Operation(summary = "单条录入")
    @SaCheckPermission("system:roster:create")
    @OperationLog(moduleCode = "system", actionCode = "system:roster:create", targetTable = "sys_person_roster")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody RosterCreateDTO dto) {
        rosterService.create(dto);
        return Result.success();
    }

    @Operation(summary = "批量导入（同事务，任一冲突整体回滚）")
    @SaCheckPermission("system:roster:create")
    @OperationLog(moduleCode = "system", actionCode = "system:roster:batch_create", targetTable = "sys_person_roster")
    @PostMapping("/batch")
    public Result<Integer> createBatch(@Valid @RequestBody RosterBatchCreateDTO dto) {
        return Result.success(rosterService.createBatch(dto));
    }

    @Operation(summary = "编辑名单（已建档禁止编辑）")
    @SaCheckPermission("system:roster:update")
    @OperationLog(moduleCode = "system", actionCode = "system:roster:update", targetTable = "sys_person_roster", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RosterUpdateDTO dto) {
        rosterService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用名单（已建档禁止停用）")
    @SaCheckPermission("system:roster:enable")
    @OperationLog(moduleCode = "system", actionCode = "system:roster:enable", targetTable = "sys_person_roster", targetId = "#id")
    @PutMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody RosterEnabledDTO dto) {
        rosterService.updateEnabled(id, dto);
        return Result.success();
    }
}
