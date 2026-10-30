package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.module.item.dto.OverduePageQuery;
import com.labmanage.module.item.service.OverdueService;
import com.labmanage.module.item.vo.OverdueItemVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/overdues")
public class OverdueController {

    private final OverdueService overdueService;

    public OverdueController(OverdueService overdueService) {
        this.overdueService = overdueService;
    }

    @GetMapping
    @RequirePermission("item:overdue:list")
    public Result<PageResult<OverdueItemVO>> page(@Valid @ModelAttribute OverduePageQuery query) {
        return Result.success(overdueService.page(query));
    }
}
