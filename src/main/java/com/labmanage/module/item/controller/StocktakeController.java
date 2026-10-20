package com.labmanage.module.item.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.StocktakeCountDTO;
import com.labmanage.module.item.dto.StocktakeCreateDTO;
import com.labmanage.module.item.dto.StocktakeExportQuery;
import com.labmanage.module.item.dto.StocktakeQuery;
import com.labmanage.module.item.service.StockTransferService;
import com.labmanage.module.item.service.StocktakeService;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.vo.ImportResultVO;
import com.labmanage.module.item.vo.StocktakeDetailVO;
import com.labmanage.module.item.vo.StocktakeVO;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 盘点管理（建单快照 → 冻结 → 实盘 → 复核调账 → 解冻）
 */
@Tag(name = "盘点管理", description = "盘点单登记、冻结库位、实盘录入、差异调账审批；支持盘点快照导出与实盘批量回导；COUNTING/PENDING_REVIEW 期间禁止库存出入库")
@RestController
@RequestMapping("/api/v1/stocktakes")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    private final StockTransferService stockTransferService;

    public StocktakeController(StocktakeService stocktakeService,
                               StockTransferService stockTransferService) {
        this.stocktakeService = stocktakeService;
        this.stockTransferService = stockTransferService;
    }

    @Operation(summary = "建盘点单（草稿）",
            description = "建单即快照库位全部账面量与余额版本；返回盘点单 ID。开始盘点前不冻结库位")
    @RequirePermission("item:stocktake:create")
    @PostMapping
    public Result<String> create(@Valid @RequestBody StocktakeCreateDTO dto) {
        return Result.success(stocktakeService.create(dto));
    }

    @Operation(summary = "分页查询盘点单")
    @RequirePermission("item:stocktake:list")
    @GetMapping("/page")
    public Result<PageResult<StocktakeVO>> page(@Valid StocktakeQuery query) {
        return Result.success(stocktakeService.page(query));
    }

    @Operation(summary = "盘点单详情（含盘点行）")
    @RequirePermission("item:stocktake:list")
    @GetMapping("/{id}")
    public Result<StocktakeDetailVO> get(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        return Result.success(stocktakeService.get(id));
    }

    @Operation(summary = "开始盘点（草稿 → 盘点中）",
            description = "开始即冻结库位：COUNTING/PENDING_REVIEW 期间该库位禁止一切库存写入；同一库位仅一张活跃盘点单")
    @RequirePermission("item:stocktake:create")
    @PostMapping("/{id}/start")
    public Result<Void> start(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        stocktakeService.start(id);
        return Result.success();
    }

    @Operation(summary = "实盘录入",
            description = "盘点中可多次录入修正（重复覆盖）；差异原因随实盘一并保存")
    @RequirePermission("item:stocktake:count")
    @PostMapping("/{id}/count")
    public Result<Void> count(@Parameter(description = "盘点单 ID") @PathVariable String id,
                              @Valid @RequestBody StocktakeCountDTO dto) {
        stocktakeService.count(id, dto);
        return Result.success();
    }

    @Operation(summary = "提交复核（盘点中 → 待复核）",
            description = "校验全部行已录入、差异行必填原因、余额版本与建单快照一致；"
                    + "差异非零自动生成 ADJUST 调账单草稿并返回调账单 ID，无差异返回空（随后确认完成）")
    @RequirePermission("item:stocktake:complete")
    @PostMapping("/{id}/submit-review")
    public Result<String> submitReview(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        return Result.success(stocktakeService.submitReview(id));
    }

    @Operation(summary = "无差异确认完成（待复核 → 已完成）",
            description = "无差异盘点单的复核确认；完成后解冻库位")
    @RequirePermission("item:stocktake:complete")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        stocktakeService.complete(id);
        return Result.success();
    }

    @Operation(summary = "差异调账审批过账",
            description = "对提交复核生成的调账单一步审批并过账：盘盈按账面均价入账、盘亏按账面均价出库；"
                    + "过账完成后自动关闭盘点单并解冻库位")
    @RequirePermission("item:adjust:approve")
    @PostMapping("/{id}/approve-adjust")
    public Result<Void> approveAdjust(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        stocktakeService.approveAdjust(id);
        return Result.success();
    }

    @Operation(summary = "取消盘点单（草稿 → 已取消）", description = "未开始盘点前可取消；已开始的盘点须走复核流程")
    @RequirePermission("item:stocktake:create")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@Parameter(description = "盘点单 ID") @PathVariable String id) {
        stocktakeService.cancel(id);
        return Result.success();
    }

    @Operation(summary = "导出盘点单列表（整个盘点管理维度，按筛选条件导出全部匹配行，xlsx/csv）",
            description = "导出盘点单：盘点单ID、库位编码、库位名称、盘点日期、状态（中文）、开始/完成/登记时间（上海时区）、调账单ID；"
                    + "镜像分页查询筛选条件（库位/状态），不分页")
    @RequirePermission("item:stocktake:export")
    @GetMapping("/export")
    public void export(@Valid StocktakeExportQuery query, HttpServletResponse response) throws IOException {
        TransferFile file = stockTransferService.exportStocktakes(query);
        writeTransferFile(file, response);
    }

    @Operation(summary = "导出盘点快照（xlsx/csv，任意状态可导）",
            description = "按盘点单导出快照行：库位编码、SKU 码、物料名称、账面数、实盘数、差异原因；"
                    + "已录入行带原值支持中断续盘，未录入行实盘数与原因留空；导出文件可直接回导")
    @RequirePermission("item:stocktake:export")
    @GetMapping("/{id}/snapshot-export")
    public void snapshotExport(
            @Parameter(description = "盘点单 ID") @PathVariable String id,
            @Parameter(description = "导出格式：xlsx / csv") @RequestParam(defaultValue = "xlsx") String format,
            HttpServletResponse response) throws IOException {
        TransferFile file = stockTransferService.exportStocktakeSnapshot(id, FileFormat.fromFileName("t." + format));
        writeTransferFile(file, response);
    }

    @Operation(summary = "批量回导实盘（xlsx/csv，复用实盘录入状态机；仅盘点中状态可录入）",
            description = "按 SKU 码定位盘点行批量录入实盘数与差异原因；实盘数与差异原因均空的行跳过不计总数（支持分批回导）；"
                    + "非法行跳过并返回失败明细，有效行一次性提交（事务原子、可重复录入修正）")
    @RequirePermission("item:stocktake:count")
    @PostMapping("/{id}/count-import")
    public Result<ImportResultVO> countImport(
            @Parameter(description = "盘点单 ID") @PathVariable String id,
            @Parameter(description = "回导文件（.xlsx / .csv，建议使用快照导出文件填写后回传）") @RequestParam("file") MultipartFile file) {
        return Result.success(stockTransferService.importStocktakeCount(id, file));
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
