package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.StockLocationCreateDTO;
import com.labmanage.module.item.dto.StockLocationQuery;
import com.labmanage.module.item.dto.StockLocationUpdateDTO;
import com.labmanage.module.item.service.StockLocationService;
import com.labmanage.module.item.vo.StockLocationVO;
import com.labmanage.module.item.vo.SysUserOptionVO;
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
 * 库位管理
 */
@Tag(name = "库位管理", description = "库位的维护与查询（库存归属具体实验室）")
@RestController
@RequestMapping("/api/v1/stock-locations")
public class StockLocationController {

    private final StockLocationService locationService;

    public StockLocationController(StockLocationService locationService) {
        this.locationService = locationService;
    }

    @Operation(summary = "分页查询库位")
    @SaCheckPermission("item:location:list")
    @GetMapping("/page")
    public Result<PageResult<StockLocationVO>> page(@Valid StockLocationQuery query) {
        return Result.success(locationService.page(query));
    }

    @Operation(summary = "保管人候选搜索", description = "按姓名/账号/学工号前缀搜索启用用户（跨模块只读，库位维护选择保管人用）")
    @SaCheckPermission("item:location:list")
    @GetMapping("/custodian-options")
    public Result<List<SysUserOptionVO>> custodianOptions(
            @Parameter(description = "关键字（姓名/账号/学工号前缀）") @RequestParam(required = false) String keyword) {
        return Result.success(locationService.custodianOptions(keyword));
    }

    @Operation(summary = "库位详情")
    @SaCheckPermission("item:location:list")
    @GetMapping("/{id}")
    public Result<StockLocationVO> get(@Parameter(description = "库位 ID") @PathVariable String id) {
        return Result.success(locationService.get(id));
    }

    @Operation(summary = "新增库位")
    @SaCheckPermission("item:location:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody StockLocationCreateDTO dto) {
        locationService.create(dto);
        return Result.success();
    }

    @Operation(summary = "修改库位")
    @SaCheckPermission("item:location:update")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody StockLocationUpdateDTO dto) {
        locationService.update(dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用库位")
    @SaCheckPermission("item:location:enable")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "库位 ID") @PathVariable String id,
            @Parameter(description = "true 启用 / false 停用") @RequestParam boolean enabled) {
        locationService.updateStatus(id, enabled);
        return Result.success();
    }

    @Operation(summary = "删除库位（逻辑删除；被库存余额/单据明细引用时拒绝）")
    @SaCheckPermission("item:location:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "库位 ID") @PathVariable String id) {
        locationService.delete(id);
        return Result.success();
    }
}
