package com.labmanage.module.item.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.StockBalanceExportQuery;
import com.labmanage.module.item.dto.StockBalanceQuery;
import com.labmanage.module.item.dto.StockBalanceWarningUpdateDTO;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.service.StockTransferService;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.vo.ImportResultVO;
import com.labmanage.module.item.vo.StockBalanceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 库存余额查询与预警配置
 */
@Tag(name = "库存余额", description = "按库位+SKU 查询当前库存、安全库存与预警状态，设置预警阈值；支持余额报表导出与期初批量导入")
@RestController
@RequestMapping("/api/v1/stock-balances")
public class StockBalanceController {

    private final StockQueryService stockQueryService;

    private final StockTransferService stockTransferService;

    public StockBalanceController(StockQueryService stockQueryService,
                                  StockTransferService stockTransferService) {
        this.stockQueryService = stockQueryService;
        this.stockTransferService = stockTransferService;
    }

    @Operation(summary = "分页查询库存余额")
    @RequirePermission("item:balance:list")
    @GetMapping("/page")
    public Result<PageResult<StockBalanceVO>> page(@Valid StockBalanceQuery query) {
        return Result.success(stockQueryService.pageBalance(query));
    }

    @Operation(summary = "设置预警配置（安全库存与补货目标库存）")
    @RequirePermission("item:balance:update")
    @PutMapping("/{id}/warning-config")
    public Result<Void> updateWarningConfig(
            @Parameter(description = "余额 ID") @PathVariable String id,
            @Valid @RequestBody StockBalanceWarningUpdateDTO dto) {
        stockQueryService.updateWarningConfig(id, dto);
        return Result.success();
    }

    @Operation(summary = "批量导入期初余额（xlsx/csv；有效行组装为一张 OPENING 期初入库草稿单，走审批过账，不直接改写余额）",
            description = "每行为一个库位+SKU 组合，仅允许当前无余额的组合；库位/SKU 须存在且启用。"
                    + "非法与冲突行跳过并返回失败明细，导入结果含草稿单据 ID")
    @RequirePermission("item:balance:import")
    @PostMapping("/import")
    public Result<ImportResultVO> importOpening(
            @Parameter(description = "导入文件（.xlsx / .csv）") @RequestParam("file") MultipartFile file) {
        return Result.success(stockTransferService.importOpeningBalances(file));
    }

    @Operation(summary = "导出库存余额（按筛选条件导出全部匹配行，xlsx/csv）")
    @RequirePermission("item:balance:export")
    @GetMapping("/export")
    public void export(@Valid StockBalanceExportQuery query, HttpServletResponse response) throws IOException {
        TransferFile file = stockTransferService.exportBalances(query);
        writeTransferFile(file, response);
    }

    @Operation(summary = "下载期初余额导入模板（xlsx/csv，仅表头）")
    @RequirePermission("item:balance:import")
    @GetMapping("/import-template")
    public void importTemplate(
            @Parameter(description = "模板格式：xlsx / csv") @RequestParam(defaultValue = "xlsx") String format,
            HttpServletResponse response) throws IOException {
        TransferFile file = stockTransferService.openingImportTemplate(FileFormat.fromFileName("t." + format));
        writeTransferFile(file, response);
    }

    private void writeTransferFile(TransferFile file, HttpServletResponse response) throws IOException {
        response.setContentType(file.contentType());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString());
        response.setContentLength(file.content().length);
        response.getOutputStream().write(file.content());
    }
}
