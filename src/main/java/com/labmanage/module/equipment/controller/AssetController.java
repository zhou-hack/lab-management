package com.labmanage.module.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.module.equipment.dto.AssetCreateRequest;
import com.labmanage.module.equipment.dto.AssetPageQuery;
import com.labmanage.module.equipment.dto.AssetTransferRequest;
import com.labmanage.module.equipment.dto.AssetUpdateRequest;
import com.labmanage.module.equipment.service.AssetService;
import com.labmanage.module.equipment.vo.AssetImportResultVO;
import com.labmanage.module.equipment.vo.AssetVO;
import com.labmanage.module.equipment.vo.CustodianOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 设备台账管理
 */
@Tag(name = "设备管理-台账")
@RestController
@RequestMapping("/api/v1/equipment/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @Operation(summary = "台账分页查询（无维护权限时收敛为在架目录视图）")
    @SaCheckPermission("equipment:asset:list")
    @GetMapping
    public Result<PageResult<AssetVO>> page(AssetPageQuery query) {
        return Result.success(assetService.page(query));
    }

    @Operation(summary = "保管人下拉选项（全部启用用户）")
    @SaCheckPermission("equipment:asset:list")
    @GetMapping("/custodian-options")
    public Result<List<CustodianOptionVO>> custodianOptions() {
        return Result.success(assetService.custodianOptions());
    }

    @Operation(summary = "新增台账")
    @SaCheckPermission("equipment:asset:create")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AssetCreateRequest request) {
        assetService.create(request);
        return Result.success();
    }

    @Operation(summary = "修改台账")
    @SaCheckPermission("equipment:asset:update")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody AssetUpdateRequest request) {
        assetService.update(id, request);
        return Result.success();
    }

    @Operation(summary = "删除台账")
    @SaCheckPermission("equipment:asset:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        assetService.delete(id);
        return Result.success();
    }

    @Operation(summary = "迁移登记")
    @SaCheckPermission("equipment:asset:transfer")
    @PutMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable String id, @Valid @RequestBody AssetTransferRequest request) {
        assetService.transfer(id, request);
        return Result.success();
    }

    @Operation(summary = "台账导出（xlsx，按当前筛选全量导出，导出文件可作导入模板）")
    @SaCheckPermission("equipment:asset:export")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(AssetPageQuery query) {
        byte[] bytes = assetService.export(query);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .withZone(ZoneId.of("Asia/Shanghai")).format(Instant.now());
        String filename = URLEncoder.encode("设备台账_" + stamp + ".xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @Operation(summary = "台账导入（xlsx，按表头列名解析；行级错误跳过并在结果中报告）")
    @SaCheckPermission("equipment:asset:import")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AssetImportResultVO> importAssets(@RequestParam("file") MultipartFile file) {
        return Result.success(assetService.importAssets(file));
    }
}
