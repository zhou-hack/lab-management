package com.labmanage.module.item.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialQuotaQuery;
import com.labmanage.module.item.dto.MaterialQuotaUpdateDTO;
import com.labmanage.module.item.dto.MaterialQuotaUpsertDTO;
import com.labmanage.module.item.service.MaterialQuotaService;
import com.labmanage.module.item.vo.MaterialQuotaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 领用定额管理（开课实例 + SKU 唯一）
 */
@Tag(name = "领用定额管理", description = "按开课实例设定每人每课时物料定额，驱动申领定额快照")
@RestController
@RequestMapping("/api/v1/material-quotas")
public class MaterialQuotaController {

    private final MaterialQuotaService quotaService;

    public MaterialQuotaController(MaterialQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Operation(summary = "批量登记定额", description = "开课实例 + 物料已存在则更新定额量，不存在则新增并启用")
    @RequirePermission("item:quota:create")
    @PostMapping
    public Result<Void> upsert(@Valid @RequestBody MaterialQuotaUpsertDTO dto) {
        quotaService.upsert(dto);
        return Result.success();
    }

    @Operation(summary = "分页查询定额")
    @RequirePermission("item:quota:list")
    @GetMapping("/page")
    public Result<PageResult<MaterialQuotaVO>> page(@Valid MaterialQuotaQuery query) {
        return Result.success(quotaService.page(query));
    }

    @Operation(summary = "修改定额量")
    @RequirePermission("item:quota:update")
    @PutMapping("/{id}")
    public Result<Void> update(@Parameter(description = "定额 ID") @PathVariable String id,
                               @Valid @RequestBody MaterialQuotaUpdateDTO dto) {
        quotaService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用定额", description = "停用后新申请不再取默认定额，历史申领快照不变")
    @RequirePermission("item:quota:enable")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@Parameter(description = "定额 ID") @PathVariable String id) {
        quotaService.enable(id);
        return Result.success();
    }

    @Operation(summary = "停用定额")
    @RequirePermission("item:quota:enable")
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@Parameter(description = "定额 ID") @PathVariable String id) {
        quotaService.disable(id);
        return Result.success();
    }
}
