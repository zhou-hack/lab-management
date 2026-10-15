package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialSkuCreateDTO;
import com.labmanage.module.item.dto.MaterialSkuQuery;
import com.labmanage.module.item.dto.MaterialSkuUpdateDTO;
import com.labmanage.module.item.service.MaterialSkuService;
import com.labmanage.module.item.vo.MaterialSkuVO;
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

/**
 * 物料档案管理
 */
@Tag(name = "物料档案管理", description = "物料 SKU 主档的维护与查询")
@RestController
@RequestMapping("/api/v1/material-skus")
public class MaterialSkuController {

    private final MaterialSkuService skuService;

    public MaterialSkuController(MaterialSkuService skuService) {
        this.skuService = skuService;
    }

    @Operation(summary = "分页查询物料")
    @SaCheckPermission("item:material:list")
    @GetMapping("/page")
    public Result<PageResult<MaterialSkuVO>> page(@Valid MaterialSkuQuery query) {
        return Result.success(skuService.page(query));
    }

    @Operation(summary = "物料详情")
    @SaCheckPermission("item:material:list")
    @GetMapping("/{id}")
    public Result<MaterialSkuVO> get(@Parameter(description = "物料 ID") @PathVariable String id) {
        return Result.success(skuService.get(id));
    }

    @Operation(summary = "新增物料")
    @SaCheckPermission("item:material:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody MaterialSkuCreateDTO dto) {
        skuService.create(dto);
        return Result.success();
    }

    @Operation(summary = "修改物料")
    @SaCheckPermission("item:material:update")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MaterialSkuUpdateDTO dto) {
        skuService.update(dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用物料")
    @SaCheckPermission("item:material:enable")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "物料 ID") @PathVariable String id,
            @Parameter(description = "true 启用 / false 停用") @RequestParam boolean enabled) {
        skuService.updateStatus(id, enabled);
        return Result.success();
    }

    @Operation(summary = "删除物料（逻辑删除；被余额/单据明细/申领明细/定额/盘点明细引用时拒绝）")
    @SaCheckPermission("item:material:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "物料 ID") @PathVariable String id) {
        skuService.delete(id);
        return Result.success();
    }
}
