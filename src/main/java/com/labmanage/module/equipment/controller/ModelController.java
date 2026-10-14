package com.labmanage.module.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.equipment.dto.ModelCreateRequest;
import com.labmanage.module.equipment.dto.ModelPageQuery;
import com.labmanage.module.equipment.dto.ModelUpdateRequest;
import com.labmanage.module.equipment.service.ModelService;
import com.labmanage.module.equipment.vo.ModelVO;
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

/**
 * 设备型号管理
 */
@Tag(name = "设备管理-型号")
@RestController
@RequestMapping("/api/v1/equipment/models")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @Operation(summary = "型号分页查询")
    @SaCheckPermission("equipment:model:list")
    @GetMapping
    public Result<PageResult<ModelVO>> page(ModelPageQuery query) {
        return Result.success(modelService.page(query));
    }

    @Operation(summary = "新增型号")
    @SaCheckPermission("equipment:model:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ModelCreateRequest request) {
        modelService.create(request);
        return Result.success();
    }

    @Operation(summary = "修改型号")
    @SaCheckPermission("equipment:model:update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody ModelUpdateRequest request) {
        modelService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除型号")
    @SaCheckPermission("equipment:model:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        modelService.delete(id);
        return Result.success();
    }
}
