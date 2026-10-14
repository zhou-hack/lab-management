package com.labmanage.module.space.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.dto.BuildingCreateRequest;
import com.labmanage.module.space.dto.BuildingPageQuery;
import com.labmanage.module.space.dto.BuildingUpdateRequest;
import com.labmanage.module.space.service.BuildingService;
import com.labmanage.module.space.vo.BuildingOptionVO;
import com.labmanage.module.space.vo.BuildingVO;
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
 * 楼栋管理
 */
@Tag(name = "空间管理-楼栋")
@RestController
@RequestMapping("/api/v1/space/buildings")
public class BuildingController {

    private final BuildingService buildingService;

    public BuildingController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @Operation(summary = "楼栋分页查询")
    @SaCheckPermission("space:building:list")
    @GetMapping
    public Result<PageResult<BuildingVO>> page(BuildingPageQuery query) {
        return Result.success(buildingService.page(query));
    }

    @Operation(summary = "楼栋下拉选项")
    @SaCheckLogin
    @GetMapping("/options")
    public Result<List<BuildingOptionVO>> options() {
        return Result.success(buildingService.options());
    }

    @Operation(summary = "新增楼栋")
    @SaCheckPermission("space:building:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody BuildingCreateRequest request) {
        buildingService.create(request);
        return Result.success();
    }

    @Operation(summary = "修改楼栋")
    @SaCheckPermission("space:building:update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody BuildingUpdateRequest request) {
        buildingService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除楼栋")
    @SaCheckPermission("space:building:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        buildingService.delete(id);
        return Result.success();
    }
}
