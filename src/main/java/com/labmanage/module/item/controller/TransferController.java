package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.TransferCreateDTO;
import com.labmanage.module.item.dto.TransferPageQuery;
import com.labmanage.module.item.dto.TransferReceiptCreateDTO;
import com.labmanage.module.item.dto.TransferReturnRequestDTO;
import com.labmanage.module.item.dto.TransferWriteoffCreateDTO;
import com.labmanage.module.item.service.TransferService;
import com.labmanage.module.item.vo.TransferDetailVO;
import com.labmanage.module.item.vo.TransferSummaryVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @RequirePermission("item:transfer:create")
    public Result<TransferDetailVO> create(@Valid @RequestBody TransferCreateDTO dto) {
        return Result.success(transferService.create(dto));
    }

    @GetMapping
    @RequirePermission(value = {"item:transfer:create", "item:transfer:receive"}, mode = RequirePermission.Mode.OR)
    public Result<PageResult<TransferSummaryVO>> page(@Valid @ModelAttribute TransferPageQuery query) {
        return Result.success(transferService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePermission(value = {"item:transfer:create", "item:transfer:receive"}, mode = RequirePermission.Mode.OR)
    public Result<TransferDetailVO> detail(@PathVariable String id) {
        return Result.success(transferService.detail(Ids.parse(id)));
    }

    @PostMapping("/{id}/receipts")
    @RequirePermission("item:transfer:receive")
    public Result<TransferDetailVO> receive(@PathVariable String id,
                                            @Valid @RequestBody TransferReceiptCreateDTO dto) {
        return Result.success(transferService.receive(Ids.parse(id), dto));
    }

    @PostMapping("/{id}/return_requests")
    @RequirePermission("item:transfer:receive")
    public Result<TransferDetailVO> returnRequest(@PathVariable String id,
                                                  @Valid @RequestBody TransferReturnRequestDTO dto) {
        return Result.success(transferService.returnRequest(Ids.parse(id), dto));
    }

    @PostMapping("/{id}/return_receipts")
    @RequirePermission("item:transfer:receive")
    public Result<TransferDetailVO> returnReceipts(@PathVariable String id,
                                                   @Valid @RequestBody TransferReceiptCreateDTO dto) {
        return Result.success(transferService.returnReceipts(Ids.parse(id), dto));
    }

    @PostMapping("/{id}/writeoffs")
    @RequirePermission("item:settlement:create")
    public Result<TransferDetailVO> writeoffs(@PathVariable String id,
                                              @Valid @RequestBody TransferWriteoffCreateDTO dto) {
        return Result.success(transferService.writeoffs(Ids.parse(id), dto));
    }
}
