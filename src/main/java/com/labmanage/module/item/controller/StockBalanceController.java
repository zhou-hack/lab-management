package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.StockBalancePageQuery;
import com.labmanage.module.item.dto.StockBalancePositionUpdateDTO;
import com.labmanage.module.item.service.StockBalanceService;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.vo.StockBalanceVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/balances")
public class StockBalanceController {

    private final StockQueryService queryService;
    private final StockBalanceService balanceService;

    public StockBalanceController(StockQueryService queryService, StockBalanceService balanceService) {
        this.queryService = queryService;
        this.balanceService = balanceService;
    }

    @GetMapping
    @RequirePermission("item:stock:list")
    public Result<PageResult<StockBalanceVO>> page(@Valid @ModelAttribute StockBalancePageQuery query) {
        return Result.success(queryService.pageBalances(query));
    }

    @PutMapping("/{id}/position")
    @RequirePermission("item:stock:inbound")
    public Result<Void> updatePosition(@PathVariable String id,
                                       @Valid @RequestBody StockBalancePositionUpdateDTO dto) {
        balanceService.updatePosition(Ids.parse(id), dto);
        return Result.success();
    }
}
