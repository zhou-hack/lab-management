package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.StockWarningQuery;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.vo.StockWarningVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存预警查询
 */
@Tag(name = "库存预警查询", description = "stock_qty ≤ safe_qty 触发预警，0 单独标缺货；建议补货量按目标量差额")
@RestController
@RequestMapping("/api/v1/stock-warnings")
public class StockWarningController {

    private final StockQueryService stockQueryService;

    public StockWarningController(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    @Operation(summary = "分页查询库存预警")
    @SaCheckPermission("item:warning:list")
    @GetMapping("/page")
    public Result<PageResult<StockWarningVO>> page(@Valid StockWarningQuery query) {
        return Result.success(stockQueryService.pageWarning(query));
    }
}
