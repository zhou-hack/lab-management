package com.labmanage.module.item.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.item.dto.MaterialCategoryCreateDTO;
import com.labmanage.module.item.dto.MaterialCategoryExportQuery;
import com.labmanage.module.item.dto.MaterialCategoryQuery;
import com.labmanage.module.item.dto.MaterialCategoryUpdateDTO;
import com.labmanage.module.item.service.MaterialCategoryService;
import com.labmanage.module.item.service.MaterialTransferService;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.vo.ImportResultVO;
import com.labmanage.module.item.vo.MaterialCategoryVO;
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
import java.util.List;

/**
 * 物料分类管理
 */
@Tag(name = "物料分类管理", description = "物料分类的维护与查询")
@RestController
@RequestMapping("/api/v1/material-categories")
public class MaterialCategoryController {

    private final MaterialCategoryService categoryService;
    private final MaterialTransferService transferService;

    public MaterialCategoryController(MaterialCategoryService categoryService, MaterialTransferService transferService) {
        this.categoryService = categoryService;
        this.transferService = transferService;
    }

    @Operation(summary = "分页查询分类")
    @SaCheckPermission("item:category:list")
    @GetMapping("/page")
    public Result<PageResult<MaterialCategoryVO>> page(@Valid MaterialCategoryQuery query) {
        return Result.success(categoryService.page(query));
    }

    @Operation(summary = "启用分类列表（下拉选择用）")
    @SaCheckPermission("item:category:list")
    @GetMapping("/list")
    public Result<List<MaterialCategoryVO>> listEnabled() {
        return Result.success(categoryService.listEnabled());
    }

    @Operation(summary = "分类详情")
    @SaCheckPermission("item:category:list")
    @GetMapping("/{id}")
    public Result<MaterialCategoryVO> get(@Parameter(description = "分类 ID") @PathVariable String id) {
        return Result.success(categoryService.get(id));
    }

    @Operation(summary = "新增分类")
    @SaCheckPermission("item:category:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody MaterialCategoryCreateDTO dto) {
        categoryService.create(dto);
        return Result.success();
    }

    @Operation(summary = "修改分类")
    @SaCheckPermission("item:category:update")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MaterialCategoryUpdateDTO dto) {
        categoryService.update(dto);
        return Result.success();
    }

    @Operation(summary = "启用/停用分类")
    @SaCheckPermission("item:category:enable")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @Parameter(description = "分类 ID") @PathVariable String id,
            @Parameter(description = "true 启用 / false 停用") @RequestParam boolean enabled) {
        categoryService.updateStatus(id, enabled);
        return Result.success();
    }

    @Operation(summary = "删除分类（逻辑删除；存在子分类或被物料引用时拒绝）")
    @SaCheckPermission("item:category:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "分类 ID") @PathVariable String id) {
        categoryService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量导入分类（xlsx/csv，父分类行须排在子分类行之前，编码冲突与非法行跳过并返回失败明细）")
    @SaCheckPermission("item:category:import")
    @PostMapping("/import")
    public Result<ImportResultVO> importCategories(
            @Parameter(description = "导入文件（.xlsx / .csv）") @RequestParam("file") MultipartFile file) {
        return Result.success(transferService.importCategories(file));
    }

    @Operation(summary = "导出分类（按筛选条件导出全部匹配行，xlsx/csv）")
    @SaCheckPermission("item:category:export")
    @GetMapping("/export")
    public void export(@Valid MaterialCategoryExportQuery query, HttpServletResponse response) throws IOException {
        TransferFile file = transferService.exportCategories(query);
        writeTransferFile(file, response);
    }

    @Operation(summary = "下载分类导入模板（xlsx/csv，仅表头）")
    @SaCheckPermission("item:category:import")
    @GetMapping("/import-template")
    public void importTemplate(
            @Parameter(description = "模板格式：xlsx / csv") @RequestParam(defaultValue = "xlsx") String format,
            HttpServletResponse response) throws IOException {
        TransferFile file = transferService.categoryImportTemplate(FileFormat.fromFileName("t." + format));
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
