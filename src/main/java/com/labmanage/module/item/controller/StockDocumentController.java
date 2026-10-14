package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.StockDocumentCreateDTO;
import com.labmanage.module.item.dto.StockDocumentQuery;
import com.labmanage.module.item.dto.StockReversalCreateDTO;
import com.labmanage.module.item.dto.StockReturnCreateDTO;
import com.labmanage.module.item.dto.StockScrapCreateDTO;
import com.labmanage.module.item.dto.StockTransferCreateDTO;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.vo.StockDocumentDetailVO;
import com.labmanage.module.item.vo.StockDocumentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存单据管理（入库/出库）
 */
@Tag(name = "库存单据管理", description = "期初/采购入库与领用出库单据：登记 → 审批 → 过账")
@RestController
@RequestMapping("/api/v1/stock-documents")
public class StockDocumentController {

    private final StockDocumentService documentService;

    public StockDocumentController(StockDocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "分页查询单据")
    @SaCheckPermission("item:stock:list")
    @GetMapping("/page")
    public Result<PageResult<StockDocumentVO>> page(@Valid StockDocumentQuery query) {
        return Result.success(documentService.page(query));
    }

    @Operation(summary = "单据详情（含明细行）")
    @SaCheckPermission("item:stock:list")
    @GetMapping("/{id}")
    public Result<StockDocumentDetailVO> get(@Parameter(description = "单据 ID") @PathVariable String id) {
        return Result.success(documentService.get(id));
    }

    @Operation(summary = "登记单据（草稿）", description = "本阶段支持期初入库、采购入库与领用出库；返回单据 ID")
    @SaCheckPermission("item:stock:create")
    @PostMapping
    public Result<String> create(@Valid @RequestBody StockDocumentCreateDTO dto) {
        return Result.success(documentService.create(dto));
    }

    @Operation(summary = "审批单据（草稿 → 已批准）")
    @SaCheckPermission("item:stock:approve")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@Parameter(description = "单据 ID") @PathVariable String id) {
        documentService.approve(id);
        return Result.success();
    }

    @Operation(summary = "取消单据（草稿/已批准 → 已取消）")
    @SaCheckPermission("item:stock:cancel")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@Parameter(description = "单据 ID") @PathVariable String id) {
        documentService.cancel(id);
        return Result.success();
    }

    @Operation(summary = "过账单据（已批准 → 已过账）",
            description = "排序锁定余额行 → 校验数量与精度 → 更新余额与移动加权均价 → 写不可变流水；缺货整笔失败")
    @SaCheckPermission("item:stock:post")
    @PostMapping("/{id}/post")
    public Result<Void> post(@Parameter(description = "单据 ID") @PathVariable String id) {
        documentService.post(id);
        return Result.success();
    }

    @Operation(summary = "登记调拨单（草稿）", description = "同物料出库位 → 入库位转移；过账双边排序锁，出库腿按来源均价转成本；返回单据 ID")
    @SaCheckPermission("item:stock:create")
    @PostMapping("/transfer")
    public Result<String> transfer(@Valid @RequestBody StockTransferCreateDTO dto) {
        return Result.success(documentService.createTransfer(dto));
    }

    @Operation(summary = "登记归还单（草稿）", description = "按原领用出库行归还入库；未指定入库位时默认回原出库位，按原出库成本快照入库；返回单据 ID")
    @SaCheckPermission("item:stock:create")
    @PostMapping("/return")
    public Result<String> returnIn(@Valid @RequestBody StockReturnCreateDTO dto) {
        return Result.success(documentService.createReturn(dto));
    }

    @Operation(summary = "登记报损报废单（草稿）", description = "库存内物料报废出库，事由必填；出库按当前账面均价结转成本；返回单据 ID")
    @SaCheckPermission("item:stock:create")
    @PostMapping("/scrap")
    public Result<String> scrap(@Valid @RequestBody StockScrapCreateDTO dto) {
        return Result.success(documentService.createScrap(dto));
    }

    @Operation(summary = "登记冲销单（草稿）", description = "对已过账的期初/采购入库、领用出库、报损报废单整单反向冲销；原出库带申领实发或已结算时禁止；返回单据 ID")
    @SaCheckPermission("item:stock:create")
    @PostMapping("/reversal")
    public Result<String> reversal(@Valid @RequestBody StockReversalCreateDTO dto) {
        return Result.success(documentService.createReversal(dto));
    }
}
