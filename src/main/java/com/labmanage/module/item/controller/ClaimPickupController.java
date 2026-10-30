package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.ClaimPageQuery;
import com.labmanage.module.item.service.ClaimPickupService;
import com.labmanage.module.item.vo.ClaimSummaryVO;
import com.labmanage.module.item.vo.PickupDetailVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/pickups")
public class ClaimPickupController {

    private final ClaimPickupService pickupService;

    public ClaimPickupController(ClaimPickupService pickupService) {
        this.pickupService = pickupService;
    }

    @GetMapping
    @RequirePermission("item:pickup:list")
    public Result<PageResult<ClaimSummaryVO>> pageMine(@Valid @ModelAttribute ClaimPageQuery query) {
        return Result.success(pickupService.pageMine(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("item:pickup:list")
    public Result<PickupDetailVO> detailMine(@PathVariable String id) {
        return Result.success(pickupService.detailMine(Ids.parse(id)));
    }
}
