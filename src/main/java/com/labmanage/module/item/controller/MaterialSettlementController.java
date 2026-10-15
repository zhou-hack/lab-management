package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialSettlementCreateDTO;
import com.labmanage.module.item.dto.MaterialSettlementQuery;
import com.labmanage.module.item.service.MaterialSettlementService;
import com.labmanage.module.item.vo.MaterialSettlementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物料核销管理（CONSUMED/LOST/DAMAGED）
 */
@Tag(name = "物料核销管理", description = "耗材消耗、丢失、损坏核销登记；归还结算由归还单过账自动生成")
@RestController
@RequestMapping("/api/v1/material-settlements")
public class MaterialSettlementController {

    private final MaterialSettlementService settlementService;

    public MaterialSettlementController(MaterialSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Operation(summary = "核销登记",
            description = "锁原出库行校验累计结算量不超过原出库量；核销不再扣库存（出库时已扣减）；返回结算 ID")
    @SaCheckPermission("item:settlement:create")
    @PostMapping
    public Result<String> settle(@Valid @RequestBody MaterialSettlementCreateDTO dto) {
        return Result.success(settlementService.settle(dto));
    }

    @Operation(summary = "分页查询结算记录")
    @SaCheckPermission("item:settlement:list")
    @GetMapping("/page")
    public Result<PageResult<MaterialSettlementVO>> page(@Valid MaterialSettlementQuery query) {
        return Result.success(settlementService.page(query));
    }
}
