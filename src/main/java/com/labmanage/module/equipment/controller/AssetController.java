package com.labmanage.module.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.equipment.dto.AssetClassifyRequest;
import com.labmanage.module.equipment.dto.AssetCreateRequest;
import com.labmanage.module.equipment.dto.AssetPageQuery;
import com.labmanage.module.equipment.dto.AssetTransferRequest;
import com.labmanage.module.equipment.dto.AssetUpdateRequest;
import com.labmanage.module.equipment.service.AssetService;
import com.labmanage.module.equipment.vo.AssetVO;
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
 * 设备台账管理
 */
@Tag(name = "设备管理-台账")
@RestController
@RequestMapping("/api/v1/equipment/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @Operation(summary = "台账分页查询（无维护权限时收敛为在架可约目录视图）")
    @SaCheckPermission("equipment:asset:list")
    @GetMapping
    public Result<PageResult<AssetVO>> page(AssetPageQuery query) {
        return Result.success(assetService.page(query));
    }

    @Operation(summary = "新增台账")
    @SaCheckPermission("equipment:asset:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AssetCreateRequest request) {
        assetService.create(request);
        return Result.success();
    }

    @Operation(summary = "修改台账")
    @SaCheckPermission("equipment:asset:update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody AssetUpdateRequest request) {
        assetService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除台账")
    @SaCheckPermission("equipment:asset:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        assetService.delete(id);
        return Result.success();
    }

    @Operation(summary = "迁移登记")
    @SaCheckPermission("equipment:asset:transfer")
    @PutMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable String id, @Valid @RequestBody AssetTransferRequest request) {
        assetService.transfer(id, request);
        return Result.success();
    }

    @Operation(summary = "价值定级")
    @SaCheckPermission("equipment:asset:classify")
    @PutMapping("/{id}/classification")
    public Result<Void> classify(@PathVariable String id, @Valid @RequestBody AssetClassifyRequest request) {
        assetService.classify(id, request);
        return Result.success();
    }
}
