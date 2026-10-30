package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialDocument;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.AssetStatus;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.QuantityMode;
import com.labmanage.module.item.enums.TrackMode;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.vo.AssetImportPreviewVO;
import com.labmanage.module.item.vo.AssetImportResultVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssetImportService {

    private final AssetImportFileParser parser;
    private final MaterialMapper materialMapper;
    private final FixedAssetMapper assetMapper;
    private final StockLocationMapper locationMapper;
    private final ItemLocationAccessService locationAccessService;
    private final StockDocumentService documentService;

    public AssetImportService(AssetImportFileParser parser, MaterialMapper materialMapper,
                              FixedAssetMapper assetMapper, StockLocationMapper locationMapper,
                              ItemLocationAccessService locationAccessService,
                              StockDocumentService documentService) {
        this.parser = parser;
        this.materialMapper = materialMapper;
        this.assetMapper = assetMapper;
        this.locationMapper = locationMapper;
        this.locationAccessService = locationAccessService;
        this.documentService = documentService;
    }

    public AssetImportPreviewVO preview(MultipartFile file, String locationId, Integer section) {
        StockLocation location = requireLocation(Ids.parse(locationId));
        AssetImportFileParser.ParseResult parsed = parser.parse(file, section == null ? 1 : section);
        String prefix = codePrefix(location);
        List<AssetImportPreviewVO.Issue> issues = new ArrayList<>(parsed.issues());
        Map<String, FixedAsset> existingAssets = existingAssets(parsed.rows(), prefix);
        List<AssetImportPreviewVO.Row> rows = new ArrayList<>();
        int existingCount = 0;
        for (AssetImportFileParser.AssetRow row : parsed.rows()) {
            String skuCode = row.materialType() == MaterialType.FIXED_ASSET
                    ? skuCode(prefix, row.rowNum()) : null;
            Material material = materialForPreview(row, skuCode, issues);
            if (row.materialType() == MaterialType.CONSUMABLE) {
                rows.add(new AssetImportPreviewVO.Row(row.rowNum(), row.materialName(), row.materialType().name(),
                        row.specification(), row.baseUnit(), row.quantity(), skuCode, null, null, null));
                continue;
            }
            int rowExisting = 0;
            for (int sequence = 1; sequence <= row.quantity(); sequence++) {
                String assetCode = assetCode(skuCode, sequence);
                FixedAsset existing = existingAssets.get(assetCode);
                if (existing == null) continue;
                if (material == null || !existing.getMaterialId().equals(material.getId())
                        || !existing.getLocationId().equals(location.getId())) {
                    issues.add(new AssetImportPreviewVO.Issue(row.rowNum(),
                            "资产编码已被其他物料或库位占用：" + assetCode));
                } else {
                    rowExisting++;
                }
            }
            existingCount += rowExisting;
            rows.add(new AssetImportPreviewVO.Row(row.rowNum(), row.materialName(), row.materialType().name(),
                    row.specification(), row.baseUnit(), row.quantity(), skuCode, assetCode(skuCode, 1),
                    assetCode(skuCode, row.quantity()), rowExisting));
        }
        int materialCount = parsed.rows().stream()
                .map(row -> row.materialType() + "\n" + row.materialName() + "\n"
                        + row.specification() + "\n" + row.baseUnit())
                .collect(Collectors.toSet()).size();
        int assetCount = parsed.rows().stream()
                .filter(row -> row.materialType() == MaterialType.FIXED_ASSET)
                .mapToInt(AssetImportFileParser.AssetRow::quantity).sum();
        long consumableQuantity = parsed.rows().stream()
                .filter(row -> row.materialType() == MaterialType.CONSUMABLE)
                .mapToLong(AssetImportFileParser.AssetRow::quantity).sum();
        return new AssetImportPreviewVO(parsed.sheetName(), materialCount, assetCount, existingCount,
                consumableQuantity, issues.isEmpty(), rows, issues);
    }

    @Transactional(rollbackFor = Exception.class)
    public AssetImportResultVO confirm(MultipartFile file, String locationId, Integer section) {
        StockLocation location = requireLocation(Ids.parse(locationId));
        int targetSection = section == null ? 1 : section;
        AssetImportFileParser.ParseResult parsed = parser.parse(file, targetSection);
        if (!parsed.issues().isEmpty()) {
            AssetImportPreviewVO.Issue issue = parsed.issues().get(0);
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "第" + issue.rowNum() + "行：" + issue.message());
        }
        String prefix = codePrefix(location);
        Map<String, FixedAsset> existingAssets = existingAssets(parsed.rows(), prefix);
        String idempotencyKey = importIdempotencyKey(file, prefix, targetSection);
        if (documentService.findInboundByIdempotency(StockDocumentService.SCENE_OPENING, idempotencyKey) != null) {
            return new AssetImportResultVO(0, 0, existingAssets.size(), 0, null, null);
        }
        List<FixedAsset> newAssets = new ArrayList<>();
        List<StockDocumentService.ImportConsumableLine> consumables = new ArrayList<>();
        int createdMaterials = 0;
        int skippedAssets = 0;
        long importedConsumableQuantity = 0;
        for (AssetImportFileParser.AssetRow row : parsed.rows()) {
            String skuCode = row.materialType() == MaterialType.FIXED_ASSET
                    ? skuCode(prefix, row.rowNum()) : null;
            Material material = requireMaterial(row, skuCode);
            if (material == null) {
                material = createMaterial(row, skuCode);
                createdMaterials++;
            }
            if (row.materialType() == MaterialType.CONSUMABLE) {
                consumables.add(new StockDocumentService.ImportConsumableLine(
                        material.getId(), BigDecimal.valueOf(row.quantity())));
                importedConsumableQuantity += row.quantity();
                continue;
            }
            for (int sequence = 1; sequence <= row.quantity(); sequence++) {
                String assetCode = assetCode(skuCode, sequence);
                FixedAsset existing = existingAssets.get(assetCode);
                if (existing != null) {
                    requireMatchingAsset(existing, material, location, assetCode);
                    skippedAssets++;
                    continue;
                }
                FixedAsset asset = new FixedAsset();
                asset.setId(IdWorker.getId());
                asset.setMaterialId(material.getId());
                asset.setAssetCode(assetCode);
                asset.setLocationId(location.getId());
                asset.setStatus(AssetStatus.IDLE);
                try {
                    assetMapper.insert(asset);
                } catch (DuplicateKeyException e) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(), "资产编码已存在：" + assetCode);
                }
                newAssets.add(asset);
            }
        }
        MaterialDocument document = null;
        if (!newAssets.isEmpty() || !consumables.isEmpty()) {
            document = documentService.createImportBatchInbound(newAssets, consumables, location.getId(),
                    StockDocumentService.SCENE_OPENING, idempotencyKey, importRemark(file));
        }
        return new AssetImportResultVO(createdMaterials, newAssets.size(), skippedAssets, importedConsumableQuantity,
                document == null ? null : String.valueOf(document.getId()),
                document == null ? null : document.getDocumentNo());
    }

    private StockLocation requireLocation(Long locationId) {
        StockLocation location = locationMapper.selectById(locationId);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在");
        }
        if (!Integer.valueOf(1).equals(location.getEnabled())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位已停用，不能导入");
        }
        locationAccessService.requireLocationAdmin(locationId);
        return location;
    }

    private Material materialForPreview(AssetImportFileParser.AssetRow row, String skuCode,
                                        List<AssetImportPreviewVO.Issue> issues) {
        Material material = skuCode == null ? null : findBySku(skuCode);
        if (material != null && !matches(material, row)) {
            issues.add(new AssetImportPreviewVO.Issue(row.rowNum(), "SKU编码已被其他物料占用：" + skuCode));
            return null;
        }
        if (material == null) material = findEquivalent(row);
        if (material != null && !Integer.valueOf(1).equals(material.getEnabled())) {
            issues.add(new AssetImportPreviewVO.Issue(row.rowNum(), "物料已停用：" + material.getMaterialName()));
        }
        return material;
    }

    private Material requireMaterial(AssetImportFileParser.AssetRow row, String skuCode) {
        Material material = skuCode == null ? null : findBySku(skuCode);
        if (material != null && !matches(material, row)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "SKU编码已被其他物料占用：" + skuCode);
        }
        if (material == null) material = findEquivalent(row);
        if (material != null && !Integer.valueOf(1).equals(material.getEnabled())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "物料已停用：" + material.getMaterialName());
        }
        return material;
    }

    private Material findBySku(String skuCode) {
        return materialMapper.selectOne(Wrappers.<Material>lambdaQuery()
                .eq(Material::getSkuCode, skuCode).last("LIMIT 1"));
    }

    private Material findEquivalent(AssetImportFileParser.AssetRow row) {
        String specification = row.specification();
        return materialMapper.selectOne(Wrappers.<Material>lambdaQuery()
                .eq(Material::getMaterialType, row.materialType())
                .eq(Material::getTrackMode, trackMode(row))
                .eq(Material::getMaterialName, row.materialName())
                .eq(specification != null, Material::getSpecification, specification)
                .isNull(specification == null, Material::getSpecification)
                .eq(Material::getBaseUnit, row.baseUnit())
                .last("LIMIT 1"));
    }

    private boolean matches(Material material, AssetImportFileParser.AssetRow row) {
        return material.getMaterialType() == row.materialType()
                && material.getTrackMode() == trackMode(row)
                && material.getMaterialName().equals(row.materialName())
                && java.util.Objects.equals(material.getSpecification(), row.specification())
                && material.getBaseUnit().equals(row.baseUnit());
    }

    private TrackMode trackMode(AssetImportFileParser.AssetRow row) {
        return row.materialType() == MaterialType.CONSUMABLE ? TrackMode.QUANTITY : TrackMode.SERIAL;
    }

    private Material createMaterial(AssetImportFileParser.AssetRow row, String skuCode) {
        Material material = new Material();
        material.setId(IdWorker.getId());
        material.setMaterialType(row.materialType());
        material.setTrackMode(trackMode(row));
        material.setSkuCode(row.materialType() == MaterialType.CONSUMABLE ? null : skuCode);
        material.setMaterialName(row.materialName());
        material.setSpecification(row.specification());
        material.setBaseUnit(row.baseUnit());
        material.setQuantityMode(QuantityMode.EXACT);
        material.setEnabled(1);
        try {
            materialMapper.insert(material);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "SKU编码已存在：" + skuCode);
        }
        return material;
    }

    private Map<String, FixedAsset> existingAssets(List<AssetImportFileParser.AssetRow> rows, String prefix) {
        List<String> codes = new ArrayList<>();
        for (AssetImportFileParser.AssetRow row : rows) {
            if (row.materialType() != MaterialType.FIXED_ASSET) continue;
            String skuCode = row.materialType() == MaterialType.FIXED_ASSET
                    ? skuCode(prefix, row.rowNum()) : null;
            for (int sequence = 1; sequence <= row.quantity(); sequence++) {
                codes.add(assetCode(skuCode, sequence));
            }
        }
        if (codes.isEmpty()) return Map.of();
        return assetMapper.selectList(Wrappers.<FixedAsset>lambdaQuery()
                        .in(FixedAsset::getAssetCode, codes)).stream()
                .collect(Collectors.toMap(FixedAsset::getAssetCode, Function.identity()));
    }

    private void requireMatchingAsset(FixedAsset asset, Material material,
                                      StockLocation location, String assetCode) {
        if (!asset.getMaterialId().equals(material.getId()) || !asset.getLocationId().equals(location.getId())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "资产编码已被其他物料或库位占用：" + assetCode);
        }
    }

    private String codePrefix(StockLocation location) {
        String source = location.getLocationCode() == null ? "" : location.getLocationCode().trim();
        if ((!source.matches(".*[A-Za-z].*")) && location.getLocationName() != null) {
            source = location.getLocationName().trim();
        }
        String prefix = source.toUpperCase().replaceAll("[^A-Z0-9_-]", "_");
        return prefix.isEmpty() ? "A703" : prefix;
    }

    private String skuCode(String prefix, int rowNum) {
        return prefix + "-" + String.format("%04d", rowNum);
    }

    private String assetCode(String skuCode, int sequence) {
        return skuCode + "-" + String.format("%03d", sequence);
    }

    private String importIdempotencyKey(MultipartFile file, String prefix, int section) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(file.getBytes());
            return "ASSET_IMPORT:" + prefix + ":" + section + ":" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "导入文件读取失败，请重新上传");
        }
    }

    private String importRemark(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "物料清单.xlsx" : file.getOriginalFilename();
        String value = "物料清单导入：" + filename;
        return value.length() > 512 ? value.substring(0, 512) : value;
    }
}
