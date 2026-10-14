package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.StocktakeCountDTO;
import com.labmanage.module.item.dto.StocktakeCreateDTO;
import com.labmanage.module.item.dto.StocktakeQuery;
import com.labmanage.module.item.service.StocktakeService;
import com.labmanage.module.item.vo.StocktakeDetailVO;
import com.labmanage.module.item.vo.StocktakeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 盘点管理（建单快照 → 冻结 → 实盘 → 复核调账 → 解冻）
 */
@Tag(name = "盘点管理", description = "盘点单登记、冻结库位、实盘录入、差异调账审批；COUNTING/PENDING_REVIEW 期间禁止库存出入库")
@RestController
@RequestMapping("/api/v1/stocktakes")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    public StocktakeController(StocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    @Operation(summary = "建盘点单（草稿）",
            description = "建单即快照库位全部账面量与余额版本；返回盘点单 ID。开始盘点前不冻结库位")
    @SaCheckPermission("item:stocktake:create")
    @PostMapping
    public Result<String> create(@Valid @RequestBody StocktakeCreateDTO dto) {
        return Result.success(stocktakeService.create(dto));
    }

    @Operation(summary = "分页查询盘点单")
    @SaCheckPermission("item:stocktake:list")
    @GetMapping("/page")
    public Result<PageResult<StocktakeVO>> page(@Valid StocktakeQuery query) {
        return Result.success(stocktakeService.page(query));
    }

    @Operation(summary = "盘点单详情（含盘点行）")
    @SaCheckPermission("item:stocktake:list")
    @GetMapping("/{id}")
    public Result<StocktakeDetailVO> get(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        return Result.success(stocktakeService.get(id));
    }

    @Operation(summary = "开始盘点（草稿 → 盘点中）",
            description = "开始即冻结库位：COUNTING/PENDING_REVIEW 期间该库位禁止一切库存写入；同一库位仅一张活跃盘点单")
    @SaCheckPermission("item:stocktake:create")
    @PostMapping("/{id}/start")
    public Result<Void> start(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        stocktakeService.start(id);
        return Result.success();
    }

    @Operation(summary = "实盘录入",
            description = "盘点中可多次录入修正（重复覆盖）；差异原因随实盘一并保存")
    @SaCheckPermission("item:stocktake:count")
    @PostMapping("/{id}/count")
    public Result<Void> count(@Parameter(description = "盘点单 ID") @PathVariable String id,
                              @Valid @RequestBody StocktakeCountDTO dto) {
        stocktakeService.count(id, dto);
        return Result.success();
    }

    @Operation(summary = "提交复核（盘点中 → 待复核）",
            description = "校验全部行已录入、差异行必填原因、余额版本与建单快照一致；"
                    + "差异非零自动生成 ADJUST 调账单草稿并返回调账单 ID，无差异返回空（随后确认完成）")
    @SaCheckPermission("item:stocktake:complete")
    @PostMapping("/{id}/submit-review")
    public Result<String> submitReview(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        return Result.success(stocktakeService.submitReview(id));
    }

    @Operation(summary = "无差异确认完成（待复核 → 已完成）",
            description = "无差异盘点单的复核确认；完成后解冻库位")
    @SaCheckPermission("item:stocktake:complete")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        stocktakeService.complete(id);
        return Result.success();
    }

    @Operation(summary = "差异调账审批过账",
            description = "对提交复核生成的调账单一步审批并过账：盘盈按账面均价入账、盘亏按账面均价出库；"
                    + "过账完成后自动关闭盘点单并解冻库位")
    @SaCheckPermission("item:adjust:approve")
    @PostMapping("/{id}/approve-adjust")
    public Result<Void> approveAdjust(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        stocktakeService.approveAdjust(id);
        return Result.success();
    }

    @Operation(summary = "取消盘点单（草稿 → 已取消）", description = "未开始盘点前可取消；已开始的盘点须走复核流程")
    @SaCheckPermission("item:stocktake:create")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        stocktakeService.cancel(id);
        return Result.success();
    }
}
