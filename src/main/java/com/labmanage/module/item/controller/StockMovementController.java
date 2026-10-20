package com.labmanage.module.item.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.StockMovementQuery;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.vo.StockMovementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存台账（流水）查询
 */
@Tag(name = "库存台账查询", description = "不可变流水的只追加台账，支持按库位/物料/单据/方向/时间过滤")
@RestController
@RequestMapping("/api/v1/stock-movements")
public class StockMovementController {

    private final StockQueryService stockQueryService;

    public StockMovementController(StockQueryService stockQueryService) {
        this.stockQueryService = stockQueryService;
    }

    @Operation(summary = "分页查询库存台账")
    @RequirePermission("item:ledger:list")
    @GetMapping("/page")
    public Result<PageResult<StockMovementVO>> page(@Valid StockMovementQuery query) {
        return Result.success(stockQueryService.pageMovement(query));
    }
}
