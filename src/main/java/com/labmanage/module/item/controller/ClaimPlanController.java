package com.labmanage.module.item.controller;

import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.module.item.dto.ClaimPreviewDTO;
import com.labmanage.module.item.service.ClaimPlanService;
import com.labmanage.module.item.vo.ClaimPreviewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/claims")
public class ClaimPlanController {

    private final ClaimPlanService claimPlanService;

    public ClaimPlanController(ClaimPlanService claimPlanService) {
        this.claimPlanService = claimPlanService;
    }

    @PostMapping("/preview")
    @RequirePermission("item:claim:create")
    public Result<ClaimPreviewVO> preview(@Valid @RequestBody ClaimPreviewDTO dto) {
        return Result.success(claimPlanService.preview(dto));
    }
}
