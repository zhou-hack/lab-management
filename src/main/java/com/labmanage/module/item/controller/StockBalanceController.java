package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.StockBalanceQuery;
import com.labmanage.module.item.dto.StockBalanceWarningUpdateDTO;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.vo.StockBalanceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存余额查询与预警配置
 */
@Tag(name = "库存余额", description = "按库位+SKU 查询当前库存、安全库存与预警状态，设置预警阈值")
@RestController
@RequestMapping("/api/v1/stock-balances")
public class StockBalanceController {

    private final StockQueryService stockQueryService;

    public StockBalanceController(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    @Operation(summary = "分页查询库存余额")
    @SaCheckPermission("item:balance:list")
    @GetMapping("/page")
    public Result<PageResult<StockBalanceVO>> page(@Valid StockBalanceQuery query) {
        return Result.success(stockQueryService.pageBalance(query));
    }

    @Operation(summary = "设置预警配置（安全库存与补货目标库存）")
    @SaCheckPermission("item:balance:update")
    @PutMapping("/{id}/warning-config")
    public Result<Void> updateWarningConfig(
            @Parameter(description = "余额 ID") @PathVariable String id,
            @Valid @RequestBody StockBalanceWarningUpdateDTO dto) {
        stockQueryService.updateWarningConfig(id, dto);
        return Result.success();
    }
}
