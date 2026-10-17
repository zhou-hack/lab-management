package com.labmanage.module.space.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.log.OperationLog;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.dto.BuildingCreateDTO;
import com.labmanage.module.space.dto.BuildingPageQueryDTO;
import com.labmanage.module.space.dto.BuildingUpdateDTO;
import com.labmanage.module.space.dto.EnabledDTO;
import com.labmanage.module.space.service.SpaceBuildingService;
import com.labmanage.module.space.vo.BuildingOptionVO;
import com.labmanage.module.space.vo.BuildingPageItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * 空间管理-楼栋
 */
@Tag(name = "空间-楼栋")
@RestController
@RequestMapping("/api/v1/space/buildings")
public class SpaceBuildingController {

    private final SpaceBuildingService buildingService;

    public SpaceBuildingController(SpaceBuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @Operation(summary = "楼栋分页列表")
    @SaCheckPermission("space:building:list")
    @GetMapping
    public Result<PageResult<BuildingPageItemVO>> page(@Valid BuildingPageQueryDTO query) {
        return Result.success(buildingService.page(query));
    }

    @Operation(summary = "启用楼栋下拉选项（可按校区过滤）")
    @SaCheckPermission("space:lab:list")
    @GetMapping("/options")
    public Result<List<BuildingOptionVO>> options(@RequestParam(required = false) Long campusId) {
        return Result.success(buildingService.options(campusId));
    }

    @Operation(summary = "新增楼栋")
    @SaCheckPermission("space:building:create")
    @OperationLog(moduleCode = "space", actionCode = "space:building:create", targetTable = "building")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody BuildingCreateDTO dto) {
        buildingService.create(dto);
        return Result.success();
    }

    @Operation(summary = "编辑楼栋（编码不可改；允许调整所属校区）")
    @SaCheckPermission("space:building:update")
    @OperationLog(moduleCode = "space", actionCode = "space:building:update", targetTable = "building", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BuildingUpdateDTO dto) {
        buildingService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用楼栋（存在启用实验室时禁止停用）")
    @SaCheckPermission("space:building:enable")
    @OperationLog(moduleCode = "space", actionCode = "space:building:enable", targetTable = "building", targetId = "#id")
    @PutMapping("/{id}/enabled")
    public Result<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody EnabledDTO dto) {
        buildingService.updateEnabled(id, dto.enabled());
        return Result.success();
    }
}
