package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.item.dto.AssetCreateDTO;
import com.labmanage.module.item.dto.AssetPageQuery;
import com.labmanage.module.item.dto.AssetUpdateDTO;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.AssetStatus;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.TrackMode;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.vo.AssetSummaryVO;
import com.labmanage.module.item.vo.FixedAssetVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FixedAssetService {

    private final FixedAssetMapper assetMapper;
    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final StockDocumentService documentService;
    private final StockQueryService queryService;
    private final ItemLocationAccessService locationAccessService;
    private final ItemTimeService timeService;

    public FixedAssetService(FixedAssetMapper assetMapper,
                             MaterialMapper materialMapper,
                             StockLocationMapper locationMapper,
                             StockDocumentService documentService,
                             StockQueryService queryService,
                             ItemLocationAccessService locationAccessService,
                             ItemTimeService timeService) {
        this.assetMapper = assetMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.documentService = documentService;
        this.queryService = queryService;
        this.locationAccessService = locationAccessService;
        this.timeService = timeService;
    }

    public PageResult<FixedAssetVO> page(AssetPageQuery query) {
        return queryService.pageAssets(query);
    }

    public List<AssetSummaryVO> summary() {
        return queryService.assetSummary();
    }

    public FixedAssetVO detail(Long id) {
        return queryService.assetDetail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public FixedAssetVO create(AssetCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        Long materialId = Ids.parse(dto.materialId());
        Long locationId = Ids.parse(dto.locationId());
        Material material = materialMapper.selectByIdForUpdate(materialId);
        if (material == null || material.getMaterialType() != MaterialType.FIXED_ASSET
                || material.getTrackMode() != TrackMode.SERIAL) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "固定资产物料不存在或跟踪方式不正确");
        }
        if (!Integer.valueOf(1).equals(material.getEnabled())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "物料已停用，不能新建固定资产");
        }
        StockLocation location = locationMapper.selectById(locationId);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在");
        }
        if (!Integer.valueOf(1).equals(location.getEnabled())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位已停用，不能建档入库");
        }
        locationAccessService.requireLocationAdmin(operatorId, locationId);

        String assetCode = dto.assetCode().trim();
        requireAssetCodeUnused(assetCode, null);
        if (dto.price() != null && (dto.price().signum() < 0 || dto.price().scale() > 2)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "资产单价不能为负且最多2位小数");
        }
        String scene = documentService.normalizeBusinessScene(dto.documentType());
        String idempotencyKey = documentService.normalizeIdempotencyKey(dto.idempotencyKey());
        documentService.requireIdempotencyAvailable(scene, idempotencyKey);

        FixedAsset asset = new FixedAsset();
        asset.setId(IdWorker.getId());
        asset.setMaterialId(materialId);
        asset.setAssetCode(assetCode);
        asset.setPrice(dto.price());
        asset.setLocationId(locationId);
        asset.setPositionText(trimToNull(dto.positionText()));
        asset.setStatus(AssetStatus.IDLE);
        try {
            assetMapper.insert(asset);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "资产编码已存在：" + assetCode);
        }
        documentService.createAssetInbound(asset, scene, idempotencyKey, dto.remark());
        return queryService.assetDetail(asset.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public FixedAssetVO update(Long id, AssetUpdateDTO dto) {
        FixedAsset asset = assetMapper.selectByIdForUpdate(id);
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "固定资产不存在");
        }
        String assetCode = dto.assetCode().trim();
        requireAssetCodeUnused(assetCode, id);
        if (dto.price() != null && (dto.price().signum() < 0 || dto.price().scale() > 2)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "资产单价不能为负且最多2位小数");
        }
        Long operatorId = SecurityUtils.currentUserId();
        locationAccessService.requireLocationAdmin(operatorId, asset.getLocationId());
        int rows = assetMapper.update(null, Wrappers.<FixedAsset>lambdaUpdate()
                .eq(FixedAsset::getId, id)
                .set(FixedAsset::getAssetCode, assetCode)
                .set(FixedAsset::getPrice, dto.price())
                .set(FixedAsset::getPositionText, trimToNull(dto.positionText()))
                .set(FixedAsset::getUpdateTime, timeService.now())
                .set(FixedAsset::getUpdateUserId, operatorId));
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "固定资产已变化，请刷新后重试");
        }
        return queryService.assetDetail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public FixedAssetVO toggleMaintenance(Long id) {
        FixedAsset asset = assetMapper.selectByIdForUpdate(id);
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "固定资产不存在");
        }
        Long operatorId = SecurityUtils.currentUserId();
        locationAccessService.requireLocationAdmin(operatorId, asset.getLocationId());
        AssetStatus nextStatus;
        if (asset.getStatus() == AssetStatus.IDLE) {
            nextStatus = AssetStatus.MAINTENANCE;
        } else if (asset.getStatus() == AssetStatus.MAINTENANCE) {
            nextStatus = AssetStatus.IDLE;
        } else {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "借出、在途、丢失或报废资产不能直接切换维修状态");
        }
        int rows = assetMapper.update(null, Wrappers.<FixedAsset>lambdaUpdate()
                .eq(FixedAsset::getId, id)
                .set(FixedAsset::getStatus, nextStatus)
                .set(FixedAsset::getUpdateTime, timeService.now())
                .set(FixedAsset::getUpdateUserId, operatorId));
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "固定资产已变化，请刷新后重试");
        }
        return queryService.assetDetail(id);
    }

    private void requireAssetCodeUnused(String assetCode, Long excludeId) {
        if (assetCode.isEmpty() || assetCode.length() > 64) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "资产编码不能为空且长度不能超过64");
        }
        Long ownerId = assetMapper.selectIdByAssetCodeIncludingDeleted(assetCode);
        if (ownerId != null && !ownerId.equals(excludeId)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "资产编码已存在：" + assetCode);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
