package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.AssetCreateDTO;
import com.labmanage.module.item.dto.AssetPageQuery;
import com.labmanage.module.item.dto.AssetUpdateDTO;
import com.labmanage.module.item.service.AssetImportService;
import com.labmanage.module.item.service.FixedAssetService;
import com.labmanage.module.item.vo.AssetImportPreviewVO;
import com.labmanage.module.item.vo.AssetImportResultVO;
import com.labmanage.module.item.vo.AssetSummaryVO;
import com.labmanage.module.item.vo.FixedAssetVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/item/assets")
public class FixedAssetController {

    private final FixedAssetService assetService;
    private final AssetImportService importService;

    public FixedAssetController(FixedAssetService assetService, AssetImportService importService) {
        this.assetService = assetService;
        this.importService = importService;
    }

    @GetMapping
    @RequirePermission("item:asset:list")
    public Result<PageResult<FixedAssetVO>> page(@Valid @ModelAttribute AssetPageQuery query) {
        return Result.success(assetService.page(query));
    }

    @GetMapping("/summary")
    @RequirePermission("item:asset:list")
    public Result<List<AssetSummaryVO>> summary() {
        return Result.success(assetService.summary());
    }

    @GetMapping("/{id}")
    @RequirePermission("item:asset:list")
    public Result<FixedAssetVO> detail(@PathVariable String id) {
        return Result.success(assetService.detail(Ids.parse(id)));
    }

    @PostMapping("/import/preview")
    @RequirePermission({"item:stock:inbound", "item:material:manage", "item:asset:manage"})
    public Result<AssetImportPreviewVO> previewImport(@RequestParam MultipartFile file,
                                                      @RequestParam String locationId,
                                                      @RequestParam(defaultValue = "1") Integer section) {
        return Result.success(importService.preview(file, locationId, section));
    }

    @PostMapping("/import")
    @RequirePermission({"item:stock:inbound", "item:material:manage", "item:asset:manage"})
    public Result<AssetImportResultVO> importAssets(@RequestParam MultipartFile file,
                                                    @RequestParam String locationId,
                                                    @RequestParam(defaultValue = "1") Integer section) {
        return Result.success(importService.confirm(file, locationId, section));
    }

    @PostMapping
    @RequirePermission("item:asset:manage")
    public Result<FixedAssetVO> create(@Valid @RequestBody AssetCreateDTO dto) {
        return Result.success(assetService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePermission("item:asset:manage")
    public Result<FixedAssetVO> update(@PathVariable String id, @Valid @RequestBody AssetUpdateDTO dto) {
        return Result.success(assetService.update(Ids.parse(id), dto));
    }

    @PostMapping("/{id}/maintenance")
    @RequirePermission("item:asset:manage")
    public Result<FixedAssetVO> toggleMaintenance(@PathVariable String id) {
        return Result.success(assetService.toggleMaintenance(Ids.parse(id)));
    }
}
