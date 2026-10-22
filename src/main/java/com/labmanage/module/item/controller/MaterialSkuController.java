package com.labmanage.module.item.controller;

import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialSkuCreateDTO;
import com.labmanage.module.item.dto.MaterialSkuExportQuery;
import com.labmanage.module.item.dto.MaterialSkuQuery;
import com.labmanage.module.item.dto.MaterialSkuUpdateDTO;
import com.labmanage.module.item.service.MaterialSkuService;
import com.labmanage.module.item.service.MaterialTransferService;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.vo.ImportResultVO;
import com.labmanage.module.item.vo.MaterialSkuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 物料档案管理
 */
@Tag(name = "物料档案管理", description = "物料 SKU 主档的维护与查询")
@RestController
@RequestMapping("/api/v1/material-skus")
public class MaterialSkuController {

    private final MaterialSkuService skuService;
    private final MaterialTransferService transferService;

    public MaterialSkuController(MaterialSkuService skuService, MaterialTransferService transferService) {
        this.skuService = skuService;
        this.transferService = transferService;
    }

    @Operation(summary = "分页查询物料")
    @RequirePermission("item:material:list")
    @GetMapping("/page")
    public Result<PageResult<MaterialSkuVO>> page(@Valid MaterialSkuQuery query) {
        return Result.success(skuService.page(query));
    }

    @Operation(summary = "物料详情")
    @RequirePermission("item:material:list")
    @GetMapping("/{id}")
    public Result<MaterialSkuVO> get(@Parameter(description = "物料 ID") @PathVariable String id) {
        return Result.success(skuService.get(id));
    }

    @Operation(summary = "新增物料")
    @RequirePermission("item:material:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody MaterialSkuCreateDTO dto) {
        skuService.create(dto);
        return Result.success();
    }

    @Operation(summary = "修改物料")
    @RequirePermission("item:material:update")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MaterialSkuUpdateDTO dto) {
        skuService.update(dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用物料")
    @RequirePermission("item:material:enable")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "物料 ID") @PathVariable String id,
            @Parameter(description = "true 启用 / false 停用") @RequestParam boolean enabled) {
        skuService.updateStatus(id, enabled);
        return Result.success();
    }

    @Operation(summary = "删除物料（逻辑删除；被余额/单据明细/申领明细/定额引用时拒绝）")
    @RequirePermission("item:material:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "物料 ID") @PathVariable String id) {
        skuService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量导入物料（xlsx/csv，编码冲突与非法行跳过并返回失败明细）")
    @RequirePermission("item:material:import")
    @PostMapping("/import")
    public Result<ImportResultVO> importSkus(
            @Parameter(description = "导入文件（.xlsx / .csv）") @RequestParam("file") MultipartFile file) {
        return Result.success(transferService.importSkus(file));
    }

    @Operation(summary = "导出物料（按筛选条件导出全部匹配行，xlsx/csv）")
    @RequirePermission("item:material:export")
    @GetMapping("/export")
    public void export(@Valid MaterialSkuExportQuery query, HttpServletResponse response) throws IOException {
        TransferFile file = transferService.exportSkus(query);
        writeTransferFile(file, response);
    }

    @Operation(summary = "下载物料导入模板（xlsx/csv，仅表头）")
    @RequirePermission("item:material:import")
    @GetMapping("/import-template")
    public void importTemplate(
            @Parameter(description = "模板格式：xlsx / csv") @RequestParam(defaultValue = "xlsx") String format,
            HttpServletResponse response) throws IOException {
        TransferFile file = transferService.skuImportTemplate(FileFormat.fromFileName("t." + format));
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
