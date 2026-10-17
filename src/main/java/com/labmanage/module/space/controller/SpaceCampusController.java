package com.labmanage.module.space.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.dto.CampusCreateDTO;
import com.labmanage.module.space.dto.CampusPageQueryDTO;
import com.labmanage.module.space.dto.CampusUpdateDTO;
import com.labmanage.module.space.dto.EnabledDTO;
import com.labmanage.module.space.service.SpaceCampusService;
import com.labmanage.module.space.vo.CampusOptionVO;
import com.labmanage.module.space.vo.CampusPageItemVO;
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
 * 空间管理-校区
 */
@Tag(name = "空间-校区")
@RestController
@RequestMapping("/api/v1/space/campuses")
public class SpaceCampusController {

    private final SpaceCampusService campusService;

    public SpaceCampusController(SpaceCampusService campusService) {
        this.campusService = campusService;
    }

    @Operation(summary = "校区分页列表")
    @SaCheckPermission("space:campus:list")
    @GetMapping
    public Result<PageResult<CampusPageItemVO>> page(@Valid CampusPageQueryDTO query) {
        return Result.success(campusService.page(query));
    }

    @Operation(summary = "启用校区下拉选项")
    @SaCheckPermission("space:campus:list")
    @GetMapping("/options")
    public Result<List<CampusOptionVO>> options() {
        return Result.success(campusService.options());
    }

    @Operation(summary = "新增校区")
    @SaCheckPermission("space:campus:create")
    @OperationLog(moduleCode = "space", actionCode = "space:campus:create", targetTable = "campus")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CampusCreateDTO dto) {
        campusService.create(dto);
        return Result.success();
    }

    @Operation(summary = "编辑校区（编码不可改）")
    @SaCheckPermission("space:campus:update")
    @OperationLog(moduleCode = "space", actionCode = "space:campus:update", targetTable = "campus", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CampusUpdateDTO dto) {
        campusService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用校区（存在启用楼栋时禁止停用）")
    @SaCheckPermission("space:campus:enable")
    @OperationLog(moduleCode = "space", actionCode = "space:campus:enable", targetTable = "campus", targetId = "#id")
    @PutMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody EnabledDTO dto) {
        campusService.updateEnabled(id, dto.enabled());
        return Result.success();
    }
}
