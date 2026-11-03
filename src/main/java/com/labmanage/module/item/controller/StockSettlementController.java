package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.module.item.dto.StockSettlementPageQuery;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.vo.StockSettlementPageItemVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/settlements")
public class StockSettlementController {

    private final StockQueryService queryService;

    public StockSettlementController(StockQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @RequirePermission("item:document:list")
    public Result<PageResult<StockSettlementPageItemVO>> page(@Valid @ModelAttribute StockSettlementPageQuery query) {
        return Result.success(queryService.pageSettlements(query));
    }
}
