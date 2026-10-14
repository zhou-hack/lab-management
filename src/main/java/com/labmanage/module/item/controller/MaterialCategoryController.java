package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialCategoryCreateDTO;
import com.labmanage.module.item.dto.MaterialCategoryQuery;
import com.labmanage.module.item.dto.MaterialCategoryUpdateDTO;
import com.labmanage.module.item.service.MaterialCategoryService;
import com.labmanage.module.item.vo.MaterialCategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 物料分类管理
 */
@Tag(name = "物料分类管理", description = "物料分类的维护与查询")
@RestController
@RequestMapping("/api/v1/material-categories")
public class MaterialCategoryController {

    private final MaterialCategoryService categoryService;

    public MaterialCategoryController(MaterialCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "分页查询分类")
    @SaCheckPermission("item:category:list")
    @GetMapping("/page")
    public Result<PageResult<MaterialCategoryVO>> page(@Valid MaterialCategoryQuery query) {
        return Result.success(categoryService.page(query));
    }

    @Operation(summary = "启用分类列表（下拉选择用）")
    @SaCheckPermission("item:category:list")
    @GetMapping("/list")
    public Result<List<MaterialCategoryVO>> listEnabled() {
        return Result.success(categoryService.listEnabled());
    }

    @Operation(summary = "分类详情")
    @SaCheckPermission("item:category:list")
    @GetMapping("/{id}")
    public Result<MaterialCategoryVO> get(@Parameter(description = "分类 ID") @PathVariable String id) {
        return Result.success(categoryService.get(id));
    }

    @Operation(summary = "新增分类")
    @SaCheckPermission("item:category:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody MaterialCategoryCreateDTO dto) {
        categoryService.create(dto);
        return Result.success();
    }

    @Operation(summary = "修改分类")
    @SaCheckPermission("item:category:update")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MaterialCategoryUpdateDTO dto) {
        categoryService.update(dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用分类")
    @SaCheckPermission("item:category:enable")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "分类 ID") @PathVariable String id,
            @Parameter(description = "true 启用 / false 停用") @RequestParam boolean enabled) {
        categoryService.updateStatus(id, enabled);
        return Result.success();
    }

    @Operation(summary = "删除分类（逻辑删除；存在子分类或被物料引用时拒绝）")
    @SaCheckPermission("item:category:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "分类 ID") @PathVariable String id) {
        categoryService.delete(id);
        return Result.success();
    }
}
