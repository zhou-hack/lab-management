package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockMovement;
import com.labmanage.module.item.enums.AssetStatus;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockMovementMapper;
import com.labmanage.module.item.validation.MaterialQuantityValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class StockPostingService {

    private final StockBalanceMapper balanceMapper;
    private final FixedAssetMapper assetMapper;
    private final StockMovementMapper movementMapper;
    private final MaterialQuantityValidator quantityValidator;

    public StockPostingService(StockBalanceMapper balanceMapper,
                               FixedAssetMapper assetMapper,
                               StockMovementMapper movementMapper,
                               MaterialQuantityValidator quantityValidator) {
        this.balanceMapper = balanceMapper;
        this.assetMapper = assetMapper;
        this.movementMapper = movementMapper;
        this.quantityValidator = quantityValidator;
    }

    @Transactional(rollbackFor = Exception.class)
    public BalanceSnapshot postConsumable(ConsumablePosting posting) {
        validateConsumablePosting(posting);
        String positionText = normalizePosition(posting.positionText());
        StockBalance balance = balanceMapper.selectForUpdate(
                posting.materialId(), posting.locationId(), positionText);
        if (balance == null) {
            balance = new StockBalance();
            balance.setId(IdWorker.getId());
            balance.setMaterialId(posting.materialId());
            balance.setLocationId(posting.locationId());
            balance.setPositionText(positionText);
            balance.setOwnedQuantity(BigDecimal.ZERO);
            balance.setAvailableQuantity(BigDecimal.ZERO);
            balance.setVersion(0);
            try {
                balanceMapper.insert(balance);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "库存余额正在并发初始化，请重试");
            }
        }

        BigDecimal beforeOwned = balance.getOwnedQuantity();
        BigDecimal beforeAvailable = balance.getAvailableQuantity();
        BigDecimal afterOwned = beforeOwned.add(posting.ownedDelta());
        BigDecimal afterAvailable = beforeAvailable.add(posting.availableDelta());
        quantityValidator.requireInteger(beforeOwned, "过账前保有数量");
        quantityValidator.requireInteger(beforeAvailable, "过账前可用数量");
        quantityValidator.requireInteger(afterOwned, "过账后保有数量");
        quantityValidator.requireInteger(afterAvailable, "过账后可用数量");
        Integer beforeVersion = balance.getVersion();
        int rows = balanceMapper.applyDelta(balance.getId(), beforeVersion,
                posting.ownedDelta(), posting.availableDelta(), posting.occurredAt(), posting.operatorId());
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库存余额已变化或数量不足，请刷新后重试");
        }
        StockMovement movement = baseMovement(posting.movementKey(), posting.movementType(), posting.direction(),
                posting.materialId(), null, posting.locationId(), posting.quantity(),
                posting.materialDocumentId(), posting.materialDocumentLineId(),
                posting.operatorId(), posting.occurredAt());
        movement.setBeforeOwnedQuantity(beforeOwned);
        movement.setAfterOwnedQuantity(afterOwned);
        movement.setBeforeAvailableQuantity(beforeAvailable);
        movement.setAfterAvailableQuantity(afterAvailable);
        insertMovement(movement);
        return new BalanceSnapshot(balance.getId(), beforeOwned, afterOwned,
                beforeAvailable, afterAvailable, beforeVersion + 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public FixedAsset transitionAsset(AssetTransitionPosting posting) {
        if (posting == null || posting.assetId() == null || posting.expectedStatus() == null
                || posting.targetStatus() == null || posting.movementLocationId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "资产过账参数不完整");
        }
        FixedAsset asset = assetMapper.selectByIdForUpdate(posting.assetId());
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "固定资产不存在");
        }
        if (asset.getStatus() != posting.expectedStatus()
                || (posting.expectedLocationId() != null
                && !posting.expectedLocationId().equals(asset.getLocationId()))) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "固定资产状态或库位已变化，请刷新后重试");
        }
        Long targetLocationId = posting.targetLocationId() == null
                ? asset.getLocationId() : posting.targetLocationId();
        int rows = assetMapper.update(null, Wrappers.<FixedAsset>lambdaUpdate()
                .eq(FixedAsset::getId, asset.getId())
                .eq(FixedAsset::getStatus, posting.expectedStatus())
                .eq(posting.expectedLocationId() != null,
                        FixedAsset::getLocationId, posting.expectedLocationId())
                .set(FixedAsset::getStatus, posting.targetStatus())
                .set(FixedAsset::getLocationId, targetLocationId)
                .set(FixedAsset::getPositionText, posting.targetPositionText())
                .set(FixedAsset::getUpdateTime, posting.occurredAt())
                .set(FixedAsset::getUpdateUserId, posting.operatorId()));
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "固定资产状态已变化，请刷新后重试");
        }
        recordAssetMovement(new AssetMovementPosting(asset.getId(), asset.getMaterialId(),
                posting.movementLocationId(), posting.movementType(), posting.direction(),
                posting.materialDocumentId(), posting.materialDocumentLineId(),
                posting.operatorId(), posting.occurredAt(), posting.movementKey()));
        return assetMapper.selectById(asset.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordAssetMovement(AssetMovementPosting posting) {
        if (posting == null || posting.fixedAssetId() == null || posting.materialId() == null
                || posting.locationId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "资产流水参数不完整");
        }
        StockMovement movement = baseMovement(posting.movementKey(), posting.movementType(), posting.direction(),
                posting.materialId(), posting.fixedAssetId(), posting.locationId(), BigDecimal.ONE,
                posting.materialDocumentId(), posting.materialDocumentLineId(),
                posting.operatorId(), posting.occurredAt());
        insertMovement(movement);
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordConsumableMovement(ConsumableMovementPosting posting) {
        if (posting == null || posting.materialId() == null || posting.locationId() == null
                || posting.beforeOwnedQuantity() == null || posting.afterOwnedQuantity() == null
                || posting.beforeAvailableQuantity() == null || posting.afterAvailableQuantity() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "耗材流水参数不完整");
        }
        quantityValidator.requirePositiveInteger(posting.quantity(), "耗材流水数量");
        quantityValidator.requireInteger(posting.beforeOwnedQuantity(), "流水前保有数量");
        quantityValidator.requireInteger(posting.afterOwnedQuantity(), "流水后保有数量");
        quantityValidator.requireInteger(posting.beforeAvailableQuantity(), "流水前可用数量");
        quantityValidator.requireInteger(posting.afterAvailableQuantity(), "流水后可用数量");
        StockMovement movement = baseMovement(posting.movementKey(), posting.movementType(), posting.direction(),
                posting.materialId(), null, posting.locationId(), posting.quantity(),
                posting.materialDocumentId(), posting.materialDocumentLineId(),
                posting.operatorId(), posting.occurredAt());
        movement.setBeforeOwnedQuantity(posting.beforeOwnedQuantity());
        movement.setAfterOwnedQuantity(posting.afterOwnedQuantity());
        movement.setBeforeAvailableQuantity(posting.beforeAvailableQuantity());
        movement.setAfterAvailableQuantity(posting.afterAvailableQuantity());
        insertMovement(movement);
    }

    private StockMovement baseMovement(String movementKey, String movementType, String direction,
                                       Long materialId, Long assetId, Long locationId, BigDecimal quantity,
                                       Long documentId, Long lineId, Long operatorId, Instant occurredAt) {
        if (movementKey == null || movementKey.isBlank() || movementKey.length() > 128
                || movementType == null || movementType.isBlank()
                || direction == null || !(direction.equals("IN") || direction.equals("OUT"))
                || quantity == null || quantity.signum() <= 0
                || documentId == null || operatorId == null || occurredAt == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "库存流水参数不完整或不合法");
        }
        StockMovement movement = new StockMovement();
        movement.setId(IdWorker.getId());
        movement.setMovementKey(movementKey);
        movement.setMovementType(movementType);
        movement.setDirection(direction);
        movement.setMaterialId(materialId);
        movement.setFixedAssetId(assetId);
        movement.setLocationId(locationId);
        movement.setQuantity(quantity);
        movement.setMaterialDocumentId(documentId);
        movement.setMaterialDocumentLineId(lineId);
        movement.setOperatorId(operatorId);
        movement.setOccurredAt(occurredAt);
        return movement;
    }

    private void insertMovement(StockMovement movement) {
        try {
            movementMapper.insert(movement);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库存流水已存在，请勿重复过账");
        }
    }

    private void validateConsumablePosting(ConsumablePosting posting) {
        if (posting == null || posting.materialId() == null || posting.locationId() == null
                || posting.ownedDelta() == null || posting.availableDelta() == null
                || (posting.ownedDelta().signum() == 0 && posting.availableDelta().signum() == 0)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "耗材过账参数不完整或数量变化为0");
        }
        quantityValidator.requirePositiveInteger(posting.quantity(), "耗材过账数量");
        quantityValidator.requireInteger(posting.ownedDelta(), "耗材保有增量");
        quantityValidator.requireInteger(posting.availableDelta(), "耗材可用增量");
    }

    private String normalizePosition(String positionText) {
        if (positionText == null || positionText.isBlank()) {
            return null;
        }
        String value = positionText.trim();
        if (value.length() > 256) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "库内位置长度不能超过256");
        }
        return value;
    }

    public record ConsumablePosting(
            Long materialId,
            Long locationId,
            String positionText,
            BigDecimal quantity,
            BigDecimal ownedDelta,
            BigDecimal availableDelta,
            String movementType,
            String direction,
            Long materialDocumentId,
            Long materialDocumentLineId,
            Long operatorId,
            Instant occurredAt,
            String movementKey
    ) {
    }

    public record AssetMovementPosting(
            Long fixedAssetId,
            Long materialId,
            Long locationId,
            String movementType,
            String direction,
            Long materialDocumentId,
            Long materialDocumentLineId,
            Long operatorId,
            Instant occurredAt,
            String movementKey
    ) {
    }

    public record ConsumableMovementPosting(
            Long materialId,
            Long locationId,
            BigDecimal quantity,
            BigDecimal beforeOwnedQuantity,
            BigDecimal afterOwnedQuantity,
            BigDecimal beforeAvailableQuantity,
            BigDecimal afterAvailableQuantity,
            String movementType,
            String direction,
            Long materialDocumentId,
            Long materialDocumentLineId,
            Long operatorId,
            Instant occurredAt,
            String movementKey
    ) {
    }

    public record AssetTransitionPosting(
            Long assetId,
            AssetStatus expectedStatus,
            Long expectedLocationId,
            AssetStatus targetStatus,
            Long targetLocationId,
            String targetPositionText,
            Long movementLocationId,
            String movementType,
            String direction,
            Long materialDocumentId,
            Long materialDocumentLineId,
            Long operatorId,
            Instant occurredAt,
            String movementKey
    ) {
    }

    public record BalanceSnapshot(
            Long balanceId,
            BigDecimal beforeOwnedQuantity,
            BigDecimal afterOwnedQuantity,
            BigDecimal beforeAvailableQuantity,
            BigDecimal afterAvailableQuantity,
            Integer afterVersion
    ) {
    }
}
