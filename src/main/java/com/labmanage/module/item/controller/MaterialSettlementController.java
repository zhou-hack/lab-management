package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialSettlementCreateDTO;
import com.labmanage.module.item.dto.MaterialSettlementQuery;
import com.labmanage.module.item.dto.SettlementIssueOptionQuery;
import com.labmanage.module.item.service.MaterialSettlementService;
import com.labmanage.module.item.vo.MaterialSettlementVO;
import com.labmanage.module.item.vo.SettlementIssueOptionVO;
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

    @Operation(summary = "可核销出库行选项分页",
            description = "核销登记下拉选择：已过账领用出库单的行（对应库存流水中的出库记录），"
                    + "含原出库量、已结算量与剩余可核销量；keyword 按物料编码/名称过滤")
    @SaCheckPermission("item:settlement:list")
    @GetMapping("/issue-options")
    public Result<PageResult<SettlementIssueOptionVO>> issueOptions(@Valid SettlementIssueOptionQuery query) {
        return Result.success(settlementService.issueOptions(query));
    }
}
