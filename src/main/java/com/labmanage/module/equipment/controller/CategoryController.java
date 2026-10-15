package com.labmanage.module.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.Result;
import com.labmanage.module.equipment.dto.CategoryCreateRequest;
import com.labmanage.module.equipment.dto.CategoryUpdateRequest;
import com.labmanage.module.equipment.service.CategoryService;
import com.labmanage.module.equipment.vo.CategoryTreeVO;
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
 * 设备分类管理（多级树）
 */
@Tag(name = "设备管理-分类")
@RestController
@RequestMapping("/api/v1/equipment/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "分类树查询（全量，含停用节点）")
    @SaCheckPermission("equipment:category:list")
    @GetMapping
    public Result<List<CategoryTreeVO>> tree() {
        return Result.success(categoryService.tree());
    }

    @Operation(summary = "新增分类")
    @SaCheckPermission("equipment:category:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CategoryCreateRequest request) {
        categoryService.create(request);
        return Result.success();
    }

    @Operation(summary = "修改分类")
    @SaCheckPermission("equipment:category:update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody CategoryUpdateRequest request) {
        categoryService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @SaCheckPermission("equipment:category:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return Result.success();
    }
}
