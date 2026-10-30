package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.ClaimReturnCreateDTO;
import com.labmanage.module.item.dto.SettlementReviewDTO;
import com.labmanage.module.item.dto.StockDocumentPageQuery;
import com.labmanage.module.item.dto.StockSettlementCreateDTO;
import com.labmanage.module.item.dto.StockInboundCreateDTO;
import com.labmanage.module.item.service.ClaimReturnService;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.service.StockSettlementService;
import com.labmanage.module.item.vo.ClaimReturnContextVO;
import com.labmanage.module.item.vo.StockDocumentDetailVO;
import com.labmanage.module.item.vo.StockDocumentVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/documents")
public class StockDocumentController {

    private final StockDocumentService documentService;
    private final ClaimReturnService returnService;
    private final StockSettlementService settlementService;

    public StockDocumentController(StockDocumentService documentService, ClaimReturnService returnService,
                                   StockSettlementService settlementService) {
        this.documentService = documentService;
        this.returnService = returnService;
        this.settlementService = settlementService;
    }

    @GetMapping
    @RequirePermission("item:document:list")
    public Result<PageResult<StockDocumentVO>> page(@Valid @ModelAttribute StockDocumentPageQuery query) {
        return Result.success(documentService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("item:document:list")
    public Result<StockDocumentDetailVO> detail(@PathVariable String id) {
        return Result.success(documentService.detail(Ids.parse(id)));
    }

    @PostMapping("/opening")
    @RequirePermission("item:stock:inbound")
    public Result<StockDocumentDetailVO> opening(@Valid @RequestBody StockInboundCreateDTO dto) {
        return Result.success(documentService.createOpening(dto));
    }

    @PostMapping("/replenishment")
    @RequirePermission("item:stock:inbound")
    public Result<StockDocumentDetailVO> replenishment(@Valid @RequestBody StockInboundCreateDTO dto) {
        return Result.success(documentService.createReplenishment(dto));
    }

    @PostMapping("/scrap")
    @RequirePermission("item:settlement:create")
    public Result<StockDocumentDetailVO> scrap(@Valid @RequestBody StockSettlementCreateDTO dto) {
        return Result.success(settlementService.createScrap(dto));
    }

    @PostMapping("/adjust")
    @RequirePermission("item:settlement:create")
    public Result<StockDocumentDetailVO> adjust(@Valid @RequestBody StockSettlementCreateDTO dto) {
        return Result.success(settlementService.createAdjust(dto));
    }

    @PostMapping("/{id}/review")
    @RequirePermission("item:settlement:review")
    public Result<StockDocumentDetailVO> review(@PathVariable String id,
                                                @Valid @RequestBody SettlementReviewDTO dto) {
        return Result.success(settlementService.review(Ids.parse(id), dto));
    }

    @GetMapping("/{sourceId}/return_context")
    @RequirePermission("item:claim:return")
    public Result<ClaimReturnContextVO> returnContext(@PathVariable String sourceId) {
        return Result.success(returnService.returnContext(Ids.parse(sourceId)));
    }

    @PostMapping("/{sourceId}/returns")
    @RequirePermission("item:claim:return")
    public Result<StockDocumentDetailVO> returns(@PathVariable String sourceId,
                                                 @Valid @RequestBody ClaimReturnCreateDTO dto) {
        return Result.success(returnService.returns(Ids.parse(sourceId), dto));
    }
}
