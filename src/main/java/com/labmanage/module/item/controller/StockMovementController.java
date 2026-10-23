package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.StockMovementCreateDTO;
import com.labmanage.module.item.dto.StockMovementQuery;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.vo.StockMovementVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存台账（流水）查询与直接登记
 */
@Tag(name = "库存台账查询", description = "不可变流水的只追加台账，支持按库位/物料/单据/申领单/方向/时间过滤；"
        + "申领发放产生的流水附带申领单号/老师/用途；支持新建流水直接登记（生成 ADJUST 调账单自动审批过账）")
@RestController
@RequestMapping("/api/v1/stock-movements")
public class StockMovementController {

    private final StockQueryService stockQueryService;

    private final StockDocumentService stockDocumentService;

    public StockMovementController(StockQueryService stockQueryService,
                                   StockDocumentService stockDocumentService) {
        this.stockQueryService = stockQueryService;
        this.stockDocumentService = stockDocumentService;
    }

    @Operation(summary = "分页查询库存台账",
            description = "按申领单 ID 过滤可查该申领发放产生的全部出入库流水；申领发放来源的流水返回申领单 ID、申领老师与用途")
    @SaCheckPermission("item:ledger:list")
    @GetMapping("/page")
    public Result<PageResult<StockMovementVO>> page(@Valid StockMovementQuery query) {
        return Result.success(stockQueryService.pageMovement(query));
    }

    @Operation(summary = "新建库存流水（生成 ADJUST 调账单并自动审批过账，立即生效）",
            description = "按库位+物料定位（无余额行时按 0 初始化）：变更后数量大于当前库存生成入库流水、"
                    + "小于则生成出库流水，方向须与差值一致；出入库时间可指定（带偏移量的 ISO 8601）；"
                    + "同一事务内自动审批过账，余额与流水立即更新并保留完整审计，事由必填")
    @SaCheckPermission(value = {"item:stock:create", "item:stock:post"})
    @PostMapping
    public Result<String> create(@Valid @RequestBody StockMovementCreateDTO dto) {
        return Result.success(stockDocumentService.createMovement(dto));
    }
}
