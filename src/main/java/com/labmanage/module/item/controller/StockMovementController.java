package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.module.item.dto.StockMovementPageQuery;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.vo.StockMovementVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/movements")
public class StockMovementController {

    private final StockQueryService queryService;

    public StockMovementController(StockQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @RequirePermission("item:movement:list")
    public Result<PageResult<StockMovementVO>> page(@Valid @ModelAttribute StockMovementPageQuery query) {
        return Result.success(queryService.pageMovements(query));
    }
}
