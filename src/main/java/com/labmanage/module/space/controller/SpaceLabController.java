package com.labmanage.module.space.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.dto.EnabledDTO;
import com.labmanage.module.space.dto.LabCreateDTO;
import com.labmanage.module.space.dto.LabPageQueryDTO;
import com.labmanage.module.space.dto.LabUpdateDTO;
import com.labmanage.module.space.dto.OpenRuleCreateDTO;
import com.labmanage.module.space.service.SpaceLabService;
import com.labmanage.module.space.vo.LabOpenRuleVO;
import com.labmanage.module.space.vo.LabPageItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 空间管理-实验室（含开放规则维护）
 */
@Tag(name = "空间-实验室")
@RestController
@RequestMapping("/api/v1/space/labs")
public class SpaceLabController {

    private final SpaceLabService labService;

    public SpaceLabController(SpaceLabService labService) {
        this.labService = labService;
    }

    @Operation(summary = "实验室分页目录")
    @SaCheckPermission("space:lab:list")
    @GetMapping
    public Result<PageResult<LabPageItemVO>> page(@Valid LabPageQueryDTO query) {
        return Result.success(labService.page(query));
    }

    @Operation(summary = "新增实验室")
    @SaCheckPermission("space:lab:create")
    @OperationLog(moduleCode = "space", actionCode = "space:lab:create", targetTable = "lab")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody LabCreateDTO dto) {
        labService.create(dto);
        return Result.success();
    }

    @Operation(summary = "编辑实验室（编码不可改；调整管理部门需同时具备新旧部门写范围）")
    @SaCheckPermission("space:lab:update")
    @OperationLog(moduleCode = "space", actionCode = "space:lab:update", targetTable = "lab", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody LabUpdateDTO dto) {
        labService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用实验室")
    @SaCheckPermission("space:lab:enable")
    @OperationLog(moduleCode = "space", actionCode = "space:lab:enable", targetTable = "lab", targetId = "#id")
    @PutMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody EnabledDTO dto) {
        labService.updateEnabled(id, dto.enabled());
        return Result.success();
    }

    @Operation(summary = "实验室开放规则列表")
    @SaCheckPermission("space:lab:list")
    @GetMapping("/{id}/open_rules")
    public Result<List<LabOpenRuleVO>> listOpenRules(@PathVariable Long id) {
        return Result.success(labService.listOpenRules(id));
    }

    @Operation(summary = "新增开放规则（同日时间窗；按实验室管理部门校验写范围）")
    @SaCheckPermission("space:lab:update")
    @OperationLog(moduleCode = "space", actionCode = "space:lab:update", targetTable = "lab_open_rule", targetId = "#id")
    @PostMapping("/{id}/open_rules")
    public Result<Void> createOpenRule(@PathVariable Long id, @Valid @RequestBody OpenRuleCreateDTO dto) {
        labService.createOpenRule(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除开放规则（逻辑删除；按实验室管理部门校验写范围）")
    @SaCheckPermission("space:lab:update")
    @OperationLog(moduleCode = "space", actionCode = "space:lab:update", targetTable = "lab_open_rule", targetId = "#ruleId")
    @DeleteMapping("/open_rules/{ruleId}")
    public Result<Void> deleteOpenRule(@PathVariable Long ruleId) {
        labService.deleteOpenRule(ruleId);
        return Result.success();
    }
}
