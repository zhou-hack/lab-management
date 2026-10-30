package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.document.entity.ApprovalTask;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.document.enums.ApprovalTaskStatus;
import com.labmanage.module.item.dto.SettlementReviewDTO;
import com.labmanage.module.item.dto.StockSettlementCreateDTO;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialDocument;
import com.labmanage.module.item.entity.MaterialDocumentLine;
import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.AssetStatus;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.TrackMode;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper.TransferLineProcessing;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.vo.StockDocumentDetailVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.module.item.validation.MaterialQuantityValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockSettlementService {

    private static final String TYPE_SCRAP = "SCRAP";
    private static final String TYPE_ADJUST = "ADJUST";
    private static final String SCENE_IN_STOCK = "IN_STOCK_SETTLE";
    private static final String REVIEW_STAGE = "ITEM_STOCK_REVIEW";
    private static final long DEFAULT_DEPARTMENT_ID = 1L;

    private final MaterialDocumentMapper documentMapper;
    private final MaterialDocumentLineMapper lineMapper;
    private final MaterialSettlementMapper settlementMapper;
    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final StockBalanceMapper balanceMapper;
    private final FixedAssetMapper assetMapper;
    private final SysUserMapper userMapper;
    private final StockPostingService postingService;
    private final StockDocumentService documentService;
    private final ItemFlowService flowService;
    private final ItemTimeService timeService;
    private final ItemLocationAccessService locationAccessService;
    private final TransferService transferService;
    private final MaterialQuantityValidator quantityValidator;

    public StockSettlementService(MaterialDocumentMapper documentMapper,
                                  MaterialDocumentLineMapper lineMapper,
                                  MaterialSettlementMapper settlementMapper,
                                  MaterialMapper materialMapper,
                                  StockLocationMapper locationMapper,
                                  StockBalanceMapper balanceMapper,
                                  FixedAssetMapper assetMapper,
                                  SysUserMapper userMapper,
                                  StockPostingService postingService,
                                  StockDocumentService documentService,
                                  ItemFlowService flowService,
                                  ItemTimeService timeService,
                                  ItemLocationAccessService locationAccessService,
                                  TransferService transferService,
                                  MaterialQuantityValidator quantityValidator) {
        this.documentMapper = documentMapper;
        this.lineMapper = lineMapper;
        this.settlementMapper = settlementMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.balanceMapper = balanceMapper;
        this.assetMapper = assetMapper;
        this.userMapper = userMapper;
        this.postingService = postingService;
        this.documentService = documentService;
        this.flowService = flowService;
        this.timeService = timeService;
        this.locationAccessService = locationAccessService;
        this.transferService = transferService;
        this.quantityValidator = quantityValidator;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StockDocumentDetailVO createScrap(StockSettlementCreateDTO dto) {
        return createInStock(TYPE_SCRAP, dto);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StockDocumentDetailVO createAdjust(StockSettlementCreateDTO dto) {
        return createInStock(TYPE_ADJUST, dto);
    }

    private StockDocumentDetailVO createInStock(String type, StockSettlementCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        Long locationId = Ids.parse(dto.locationId());
        StockLocation location = locationMapper.selectByIdForUpdate(locationId);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "实际库位不存在");
        }
        if (!Integer.valueOf(1).equals(location.getEnabled())) {
            throw conflict("实际库位已停用，不能登记在库核销");
        }
        locationAccessService.requireLocationAdmin(operatorId, locationId);
        String idem = documentService.normalizeIdempotencyKey(dto.idempotencyKey());
        MaterialDocument existing = documentMapper.selectByOperatorTypeIdempotencyForUpdate(operatorId, type, idem);
        if (existing != null) {
            if (!SCENE_IN_STOCK.equals(existing.getBusinessScene()) || !locationId.equals(existing.getLocationId())) {
                throw conflict("该幂等键已用于其他在库核销单");
            }
            return documentService.detail(existing.getId());
        }

        List<CreateLine> resolved = resolveCreateLines(type, dto.lines(), locationId);
        lockAndValidateCreateStock(resolved, locationId);
        Instant now = timeService.now();
        MaterialDocument document = createDocument(type, operatorId, locationId, idem, dto.remark(), now);
        for (CreateLine line : resolved) {
            insertLine(document.getId(), line.material().getId(), line.assetId(), line.quantity(), line.positionText());
            MaterialSettlement settlement = new MaterialSettlement();
            settlement.setId(IdWorker.getId());
            settlement.setMaterialDocumentId(document.getId());
            settlement.setMaterialId(line.material().getId());
            settlement.setFixedAssetId(line.assetId());
            settlement.setSettlementType(type);
            settlement.setQuantity(line.quantity());
            settlement.setTargetAssetStatus(line.targetAssetStatus());
            settlement.setReason(line.reason().trim());
            settlementMapper.insert(settlement);
        }
        createReviewTask(document, operatorId, now);
        appendEvent(document.getBizDocumentId(), "SETTLEMENT_SUBMIT", null, "PENDING_REVIEW",
                operatorId, now, dto.remark(), "SETTLEMENT_SUBMIT:" + document.getId(), null);
        return documentService.detail(document.getId());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StockDocumentDetailVO review(Long id, SettlementReviewDTO dto) {
        Long reviewerId = SecurityUtils.currentUserId();
        MaterialDocument snapshot = requireReviewable(id);
        boolean transferSettlement = TransferService.TYPE_SETTLEMENT.equals(snapshot.getDocumentType());
        MaterialDocument original = null;
        Long locationId;
        if (transferSettlement) {
            if (snapshot.getSourceMaterialDocumentId() == null) {
                throw conflict("在途核销单缺少原移动单引用");
            }
            MaterialDocument originalSnapshot = documentMapper.selectById(snapshot.getSourceMaterialDocumentId());
            requireOriginalTransfer(originalSnapshot);
            original = documentMapper.selectByIdForUpdate(originalSnapshot.getId());
            requireOriginalTransfer(original);
            locationId = original.getFromLocationId();
            requireLocation(locationId);
        } else {
            locationId = snapshot.getLocationId();
            requireLocation(locationId);
        }
        MaterialDocument document = documentMapper.selectByIdForUpdate(id);
        requireReviewable(document);
        if (transferSettlement && !original.getId().equals(document.getSourceMaterialDocumentId())) {
            throw conflict("在途核销单来源已变化");
        }
        if (!transferSettlement && !locationId.equals(document.getLocationId())) {
            throw conflict("在库核销单实际库位已变化");
        }
        if (!"PENDING_REVIEW".equals(document.getReviewStatus())
                || !dto.version().equals(document.getVersion())) {
            throw conflict("核销单已复核或版本已变化，请刷新后重试");
        }
        if (reviewerId.equals(document.getOperatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "复核人不能是核销经办人");
        }
        locationAccessService.requireLocationAdmin(reviewerId, locationId);

        List<MaterialDocumentLine> documentLines = lineMapper.selectByDocumentIdForUpdate(id);
        Map<String, MaterialDocumentLine> linesByKey = indexDocumentLines(documentLines);
        List<MaterialSettlement> settlements = settlementMapper.selectByDocumentIdForUpdate(id);
        if (settlements.isEmpty()) {
            throw conflict("核销单没有核销明细");
        }
        boolean approve = Boolean.TRUE.equals(dto.approve());
        ReviewResources resources = approve
                ? transferSettlement
                ? lockAndValidateTransferReview(document, original, settlements, linesByKey)
                : lockAndValidateInStockReview(document, locationId, settlements, linesByKey)
                : new ReviewResources(Map.of(), Map.of());

        Instant now = timeService.now();
        String reviewStatus = approve ? "APPROVED" : "REJECTED";
        int updated = documentMapper.reviewSettlement(id, dto.version(), reviewStatus, reviewerId, now,
                trimToNull(dto.reason()), approve ? now : null);
        if (updated != 1) {
            throw conflict("核销单已被其他复核人处理，请刷新后重试");
        }
        String previousTransferStatus = null;
        String nextTransferStatus = null;
        if (approve) {
            if (transferSettlement) {
                previousTransferStatus = original.getTransferStatus();
                postTransferSettlement(document, original, settlements, linesByKey, resources, reviewerId, now);
                nextTransferStatus = transferService.recomputeStatus(original, reviewerId, now, null, null);
            } else {
                postInStockSettlement(document, locationId, settlements, linesByKey, resources, reviewerId, now);
            }
        }

        ApprovalTask task = flowService.findLatestTask(document.getBizDocumentId(), REVIEW_STAGE, 1);
        if (task == null) {
            throw conflict("核销复核任务不存在");
        }
        flowService.completeTask(task.getId(), approve ? ApprovalTaskStatus.APPROVED : ApprovalTaskStatus.REJECTED,
                reviewerId, trimToNull(dto.reason()));
        appendEvent(document.getBizDocumentId(), "SETTLEMENT_REVIEW", "PENDING_REVIEW", reviewStatus,
                reviewerId, now, dto.reason(), "SETTLEMENT_REVIEW:" + document.getId(), task.getId());
        if (transferSettlement && approve) {
            appendEvent(original.getBizDocumentId(), "TRANSFER_SETTLEMENT_REVIEW", previousTransferStatus,
                    nextTransferStatus, reviewerId, now, dto.reason(),
                    "TRANSFER_SETTLEMENT_REVIEW:" + original.getId() + ":" + document.getId(), null);
        }
        return documentService.detail(id);
    }

    private List<CreateLine> resolveCreateLines(String type, List<StockSettlementCreateDTO.Line> inputs,
                                                Long locationId) {
        List<CreateLine> result = new ArrayList<>();
        Set<Long> assetIds = new HashSet<>();
        Set<String> consumableKeys = new HashSet<>();
        for (StockSettlementCreateDTO.Line input : inputs) {
            if (input.quantity() != null) {
                quantityValidator.requirePositiveInteger(input.quantity(), "核销数量");
            }
            if (hasText(input.assetId())) {
                Long assetId = Ids.parse(input.assetId());
                if (!assetIds.add(assetId)) {
                    throw badRequest("同一台固定资产在一次核销中只能出现一次");
                }
                FixedAsset asset = assetMapper.selectById(assetId);
                if (asset == null) {
                    throw badRequest("固定资产不存在：" + assetId);
                }
                if (hasText(input.materialId()) && !asset.getMaterialId().equals(Ids.parse(input.materialId()))) {
                    throw badRequest("固定资产与物料不匹配：" + assetId);
                }
                Material material = requireMaterial(asset.getMaterialId(), MaterialType.FIXED_ASSET);
                BigDecimal quantity = input.quantity() == null ? BigDecimal.ONE : input.quantity();
                if (quantity.compareTo(BigDecimal.ONE) != 0) {
                    throw badRequest("固定资产核销必须逐台且每行数量为1");
                }
                String targetStatus;
                if (TYPE_SCRAP.equals(type)) {
                    if (hasText(input.targetAssetStatus()) && !"SCRAPPED".equals(input.targetAssetStatus())) {
                        throw badRequest("固定资产SCRAP的目标状态只能为SCRAPPED");
                    }
                    targetStatus = "SCRAPPED";
                } else {
                    if (!hasText(input.targetAssetStatus())) {
                        throw badRequest("固定资产ADJUST必须填写targetAssetStatus");
                    }
                    targetStatus = input.targetAssetStatus();
                }
                if (asset.getStatus() == AssetStatus.ON_LOAN || asset.getStatus() == AssetStatus.IN_TRANSIT
                        || asset.getStatus() == AssetStatus.LOST || asset.getStatus() == AssetStatus.SCRAPPED) {
                    throw conflict("仅IDLE/MAINTENANCE的在库固定资产可直接核销：" + asset.getAssetCode());
                }
                if (!locationId.equals(asset.getLocationId())) {
                    throw conflict("固定资产不在实际核销库位：" + asset.getAssetCode());
                }
                result.add(new CreateLine(material, assetId, quantity, asset.getPositionText(),
                        targetStatus, input.reason()));
            } else {
                if (!hasText(input.materialId())) {
                    throw badRequest("耗材核销行必须填写materialId");
                }
                Long materialId = Ids.parse(input.materialId());
                Material material = requireMaterial(materialId, MaterialType.CONSUMABLE);
                if (input.quantity() == null || input.quantity().signum() <= 0) {
                    throw badRequest("耗材核销调减数量必须大于0");
                }
                if (hasText(input.targetAssetStatus())) {
                    throw badRequest("耗材不能填写targetAssetStatus");
                }
                String positionText = normalizePosition(input.positionText());
                if (!consumableKeys.add(String.valueOf(materialId))) {
                    throw badRequest("同一耗材在一次在库核销中只能出现一次");
                }
                result.add(new CreateLine(material, null, input.quantity(), positionText, null, input.reason()));
            }
        }
        result.sort(Comparator.comparing((CreateLine line) -> line.material().getId())
                .thenComparing(line -> line.assetId(), Comparator.nullsFirst(Long::compareTo))
                .thenComparing(line -> line.positionText(), Comparator.nullsFirst(String::compareTo)));
        return result;
    }

    private void lockAndValidateCreateStock(List<CreateLine> lines, Long locationId) {
        for (CreateLine line : lines) {
            if (line.material().getMaterialType() == MaterialType.CONSUMABLE) {
                StockBalance balance = balanceMapper.selectForUpdate(line.material().getId(), locationId,
                        line.positionText());
                if (balance == null || balance.getOwnedQuantity().compareTo(line.quantity()) < 0
                        || balance.getAvailableQuantity().compareTo(line.quantity()) < 0) {
                    throw conflict("实际库位该position耗材保有或可用数量不足：" + line.material().getMaterialName());
                }
            } else {
                FixedAsset asset = assetMapper.selectByIdForUpdate(line.assetId());
                if (asset == null || !locationId.equals(asset.getLocationId())
                        || (asset.getStatus() != AssetStatus.IDLE && asset.getStatus() != AssetStatus.MAINTENANCE)) {
                    throw conflict("固定资产状态或库位已变化，不能登记在库核销：" + line.assetId());
                }
            }
        }
    }

    private ReviewResources lockAndValidateInStockReview(MaterialDocument document, Long locationId,
                                                         List<MaterialSettlement> settlements,
                                                         Map<String, MaterialDocumentLine> linesByKey) {
        Map<Long, Material> materials = loadMaterials(linesByKey.values());
        Map<BalanceKey, BigDecimal> demands = new HashMap<>();
        Map<Long, FixedAsset> assets = new HashMap<>();
        for (MaterialSettlement settlement : settlements) {
            MaterialDocumentLine line = requireMatchingLine(settlement, linesByKey);
            requireQuantityMatch(settlement, line);
            Material material = materials.get(settlement.getMaterialId());
            if (material == null || !document.getDocumentType().equals(settlement.getSettlementType())) {
                throw conflict("核销明细物料或类型与单据不一致");
            }
            if (settlement.getSourceMaterialDocumentLineId() != null) {
                throw conflict("在库核销不得引用原出库或原移动行");
            }
            if (material.getMaterialType() == MaterialType.CONSUMABLE) {
                if (settlement.getFixedAssetId() != null || settlement.getTargetAssetStatus() != null) {
                    throw conflict("耗材核销明细包含资产字段");
                }
                demands.merge(new BalanceKey(material.getId(), line.getPositionText()),
                        settlement.getQuantity(), BigDecimal::add);
            } else {
                if (settlement.getFixedAssetId() == null || settlement.getTargetAssetStatus() == null) {
                    throw conflict("固定资产核销明细缺少资产或目标状态");
                }
                AssetStatus target = parseTargetStatus(settlement.getTargetAssetStatus());
                FixedAsset asset = assetMapper.selectByIdForUpdate(settlement.getFixedAssetId());
                if (asset == null || !material.getId().equals(asset.getMaterialId())
                        || !locationId.equals(asset.getLocationId())
                        || (asset.getStatus() != AssetStatus.IDLE && asset.getStatus() != AssetStatus.MAINTENANCE)) {
                    throw conflict("固定资产必须仍在实际库位且保持合法在库状态：" + settlement.getFixedAssetId());
                }
                if (TYPE_SCRAP.equals(document.getDocumentType()) && target != AssetStatus.SCRAPPED) {
                    throw conflict("固定资产SCRAP复核目标状态必须为SCRAPPED");
                }
                assets.put(asset.getId(), asset);
            }
        }
        Map<BalanceKey, StockBalance> balances = new HashMap<>();
        List<Map.Entry<BalanceKey, BigDecimal>> orderedDemands = demands.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(BalanceKey::materialId)
                        .thenComparing(BalanceKey::positionText, Comparator.nullsFirst(String::compareTo))))
                .toList();
        for (Map.Entry<BalanceKey, BigDecimal> entry : orderedDemands) {
            StockBalance balance = balanceMapper.selectForUpdate(entry.getKey().materialId(), locationId,
                    entry.getKey().positionText());
            if (balance == null || balance.getOwnedQuantity().compareTo(entry.getValue()) < 0
                    || balance.getAvailableQuantity().compareTo(entry.getValue()) < 0) {
                throw conflict("复核时实际库位耗材保有或可用数量不足");
            }
            balances.put(entry.getKey(), balance);
        }
        return new ReviewResources(balances, assets);
    }

    private ReviewResources lockAndValidateTransferReview(MaterialDocument document, MaterialDocument original,
                                                          List<MaterialSettlement> settlements,
                                                          Map<String, MaterialDocumentLine> linesByKey) {
        List<MaterialDocumentLine> sourceLines = lineMapper.selectByDocumentIdForUpdate(original.getId());
        Map<Long, MaterialDocumentLine> sourceById = sourceLines.stream()
                .collect(Collectors.toMap(MaterialDocumentLine::getId, Function.identity()));
        Map<Long, TransferLineProcessing> processing = lineMapper.selectTransferProcessingBySourceDocument(original.getId())
                .stream().collect(Collectors.toMap(TransferLineProcessing::sourceLineId, Function.identity()));
        Map<Long, Material> materials = loadMaterials(sourceLines);
        Map<Long, BigDecimal> demands = new HashMap<>();
        Map<Long, FixedAsset> assets = new HashMap<>();
        Map<BalanceKey, StockBalance> balances = new HashMap<>();
        for (MaterialSettlement settlement : settlements) {
            MaterialDocumentLine childLine = requireMatchingLine(settlement, linesByKey);
            requireQuantityMatch(settlement, childLine);
            Long sourceLineId = settlement.getSourceMaterialDocumentLineId();
            MaterialDocumentLine sourceLine = sourceById.get(sourceLineId);
            if (sourceLine == null || !sourceLineId.equals(childLine.getSourceMaterialDocumentLineId())) {
                throw conflict("在途核销行必须同时引用同一原移动行");
            }
            Material material = materials.get(settlement.getMaterialId());
            if (material == null || !sourceLine.getMaterialId().equals(settlement.getMaterialId())
                    || !"LOST".equals(settlement.getSettlementType()) && !"SCRAP".equals(settlement.getSettlementType())) {
                throw conflict("在途核销明细物料或类型不合法");
            }
            demands.merge(sourceLineId, settlement.getQuantity(), BigDecimal::add);
            if (material.getMaterialType() == MaterialType.FIXED_ASSET) {
                AssetStatus target = parseTargetStatus(settlement.getTargetAssetStatus());
                if (target != AssetStatus.LOST && target != AssetStatus.SCRAPPED) {
                    throw conflict("在途固定资产核销目标状态只能为LOST/SCRAPPED");
                }
                FixedAsset asset = assetMapper.selectByIdForUpdate(settlement.getFixedAssetId());
                if (asset == null || !asset.getId().equals(sourceLine.getFixedAssetId())
                        || asset.getStatus() != AssetStatus.IN_TRANSIT
                        || !original.getFromLocationId().equals(asset.getLocationId())) {
                    throw conflict("固定资产必须保持源库位IN_TRANSIT状态才能复核：" + settlement.getFixedAssetId());
                }
                assets.put(asset.getId(), asset);
            } else if (settlement.getTargetAssetStatus() != null || settlement.getFixedAssetId() != null) {
                throw conflict("耗材在途核销明细包含资产字段");
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : demands.entrySet()) {
            TransferLineProcessing amounts = processing.get(entry.getKey());
            if (amounts == null || entry.getValue().compareTo(amounts.remainingQuantity()) > 0) {
                throw conflict("复核数量不能超过原移动行剩余在途量：" + entry.getKey());
            }
        }
        for (MaterialSettlement settlement : settlements) {
            Material material = materials.get(settlement.getMaterialId());
            if (material.getMaterialType() != MaterialType.CONSUMABLE) {
                continue;
            }
            MaterialDocumentLine sourceLine = sourceById.get(settlement.getSourceMaterialDocumentLineId());
            BalanceKey key = new BalanceKey(material.getId(), sourceLine.getPositionText());
            if (!balances.containsKey(key)) {
                StockBalance balance = balanceMapper.selectForUpdate(material.getId(), original.getFromLocationId(),
                        sourceLine.getPositionText());
                if (balance == null) {
                    throw conflict("源库位对应position的耗材余额不存在，无法记录在途核销流水");
                }
                balances.put(key, balance);
            }
        }
        return new ReviewResources(balances, assets);
    }

    private void postInStockSettlement(MaterialDocument document, Long locationId,
                                       List<MaterialSettlement> settlements,
                                       Map<String, MaterialDocumentLine> linesByKey,
                                       ReviewResources resources, Long operatorId, Instant now) {
        for (MaterialSettlement settlement : settlements) {
            MaterialDocumentLine line = requireMatchingLine(settlement, linesByKey);
            if (settlement.getFixedAssetId() == null) {
                postingService.postConsumable(new StockPostingService.ConsumablePosting(
                        settlement.getMaterialId(), locationId, line.getPositionText(), settlement.getQuantity(),
                        settlement.getQuantity().negate(), settlement.getQuantity().negate(),
                        document.getDocumentType(), "OUT", document.getId(), line.getId(), operatorId, now,
                        movementKey(document.getId(), settlement.getId(), "OUT")));
            } else {
                FixedAsset asset = resources.assets().get(settlement.getFixedAssetId());
                AssetStatus targetStatus = parseTargetStatus(settlement.getTargetAssetStatus());
                String direction = targetStatus == AssetStatus.IDLE ? "IN" : "OUT";
                postingService.transitionAsset(new StockPostingService.AssetTransitionPosting(
                        asset.getId(), asset.getStatus(), locationId,
                        targetStatus, locationId, asset.getPositionText(),
                        locationId, document.getDocumentType(), direction, document.getId(), line.getId(),
                        operatorId, now, movementKey(document.getId(), settlement.getId(), direction)));
            }
        }
    }

    private void postTransferSettlement(MaterialDocument document, MaterialDocument original,
                                        List<MaterialSettlement> settlements,
                                        Map<String, MaterialDocumentLine> linesByKey,
                                        ReviewResources resources, Long operatorId, Instant now) {
        for (MaterialSettlement settlement : settlements) {
            MaterialDocumentLine childLine = requireMatchingLine(settlement, linesByKey);
            String movementType = TransferService.SCENE_SETTLE + "_" + settlement.getSettlementType();
            if (settlement.getFixedAssetId() == null) {
                MaterialDocumentLine sourceLine = lineMapper.selectById(settlement.getSourceMaterialDocumentLineId());
                BalanceKey key = new BalanceKey(settlement.getMaterialId(), sourceLine.getPositionText());
                StockBalance balance = resources.balances().get(key);
                postingService.recordConsumableMovement(new StockPostingService.ConsumableMovementPosting(
                        settlement.getMaterialId(), original.getFromLocationId(), settlement.getQuantity(),
                        balance.getOwnedQuantity(), balance.getOwnedQuantity(),
                        balance.getAvailableQuantity(), balance.getAvailableQuantity(), movementType, "OUT",
                        document.getId(), childLine.getId(), operatorId, now,
                        movementKey(document.getId(), settlement.getId(), "OUT")));
            } else {
                postingService.transitionAsset(new StockPostingService.AssetTransitionPosting(
                        settlement.getFixedAssetId(), AssetStatus.IN_TRANSIT, original.getFromLocationId(),
                        parseTargetStatus(settlement.getTargetAssetStatus()), original.getFromLocationId(),
                        resources.assets().get(settlement.getFixedAssetId()).getPositionText(),
                        original.getFromLocationId(), movementType, "OUT", document.getId(), childLine.getId(),
                        operatorId, now, movementKey(document.getId(), settlement.getId(), "OUT")));
            }
        }
    }

    private MaterialDocument createDocument(String type, Long operatorId, Long locationId,
                                            String idem, String remark, Instant now) {
        Long documentId = IdWorker.getId();
        String documentNo = "IM" + documentId;
        SysUser operator = userMapper.selectById(operatorId);
        BizDocument shared = new BizDocument();
        shared.setId(IdWorker.getId());
        shared.setDocumentNo(documentNo);
        shared.setDocumentType("ITEM_" + type + "_IN_STOCK");
        shared.setOwnerUserId(operatorId);
        shared.setDepartmentId(operator == null || operator.getDepartmentId() == null
                ? DEFAULT_DEPARTMENT_ID : operator.getDepartmentId());
        shared.setSubmittedAt(now);
        shared.setIdempotencyKey(idem);
        flowService.createDocument(shared);

        MaterialDocument document = new MaterialDocument();
        document.setId(documentId);
        document.setBizDocumentId(shared.getId());
        document.setDocumentNo(documentNo);
        document.setDocumentType(type);
        document.setBusinessScene(SCENE_IN_STOCK);
        document.setLocationId(locationId);
        document.setReviewStatus("PENDING_REVIEW");
        document.setOperatorId(operatorId);
        document.setOccurredAt(now);
        document.setPostedAt(null);
        document.setIdempotencyKey(idem);
        document.setVersion(0);
        document.setRemark(trimToNull(remark));
        try {
            documentMapper.insert(document);
        } catch (DuplicateKeyException e) {
            throw conflict("在库核销单编号或幂等键冲突，请重试");
        }
        return document;
    }

    private MaterialDocumentLine insertLine(Long documentId, Long materialId, Long assetId,
                                            BigDecimal quantity, String positionText) {
        MaterialDocumentLine line = new MaterialDocumentLine();
        line.setId(IdWorker.getId());
        line.setMaterialDocumentId(documentId);
        line.setMaterialId(materialId);
        line.setFixedAssetId(assetId);
        line.setQuantity(quantity);
        line.setPositionText(positionText);
        lineMapper.insert(line);
        return line;
    }

    private void createReviewTask(MaterialDocument document, Long operatorId, Instant now) {
        ApprovalTask task = new ApprovalTask();
        task.setId(IdWorker.getId());
        task.setDocumentId(document.getBizDocumentId());
        task.setStageCode(REVIEW_STAGE);
        task.setSequenceNo(1);
        task.setRoundNo(1);
        task.setRequiredRole("ADMIN");
        task.setAvailableAt(now);
        task.setTimeoutAction("NONE");
        ApprovalTask persisted = flowService.createTask(task);
        appendEvent(document.getBizDocumentId(), "REVIEW_TASK_READY", null, "PENDING_REVIEW",
                operatorId, now, null, "REVIEW_TASK_READY:" + task.getId(), persisted.getId());
    }

    private void appendEvent(Long sharedDocumentId, String type, String fromStatus, String toStatus,
                             Long actorId, Instant now, String reason, String requestKey, Long taskId) {
        DocumentEvent event = new DocumentEvent();
        event.setId(IdWorker.getId());
        event.setDocumentId(sharedDocumentId);
        event.setEventType(type);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setActorId(actorId);
        event.setActorType("USER");
        event.setOccurredAt(now);
        event.setReason(trimToNull(reason));
        event.setRequestKey(requestKey);
        event.setTaskId(taskId);
        flowService.appendEvent(event);
    }

    private MaterialDocument requireReviewable(Long id) {
        MaterialDocument document = documentMapper.selectById(id);
        return requireReviewable(document);
    }

    private MaterialDocument requireReviewable(MaterialDocument document) {
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "核销单不存在");
        }
        if (!TYPE_SCRAP.equals(document.getDocumentType()) && !TYPE_ADJUST.equals(document.getDocumentType())
                && !TransferService.TYPE_SETTLEMENT.equals(document.getDocumentType())) {
            throw conflict("仅SCRAP/ADJUST/TRANSFER_SETTLEMENT可调用核销复核");
        }
        if ((TYPE_SCRAP.equals(document.getDocumentType()) || TYPE_ADJUST.equals(document.getDocumentType()))
                && !SCENE_IN_STOCK.equals(document.getBusinessScene())) {
            throw conflict("SCRAP/ADJUST复核仅适用于独立在库核销单");
        }
        if (TransferService.TYPE_SETTLEMENT.equals(document.getDocumentType())
                && !TransferService.SCENE_SETTLE.equals(document.getBusinessScene())) {
            throw conflict("TRANSFER_SETTLEMENT复核仅适用于在途核销子单");
        }
        return document;
    }

    private void requireOriginalTransfer(MaterialDocument original) {
        if (original == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "原移动单不存在");
        }
        if (!TransferService.TYPE_TRANSFER.equals(original.getDocumentType())
                || !TransferService.SCENE_SEND.equals(original.getBusinessScene())
                || original.getPostedAt() == null || original.getFromLocationId() == null) {
            throw conflict("在途核销来源不是已过账的原移动单");
        }
    }

    private void requireLocation(Long locationId) {
        if (locationId == null || locationMapper.selectByIdForUpdate(locationId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "核销权限库位不存在");
        }
    }

    private Material requireMaterial(Long id, MaterialType expectedType) {
        Material material = materialMapper.selectById(id);
        TrackMode expectedTrack = expectedType == MaterialType.CONSUMABLE ? TrackMode.QUANTITY : TrackMode.SERIAL;
        if (material == null || material.getMaterialType() != expectedType || material.getTrackMode() != expectedTrack) {
            throw badRequest("物料不存在或跟踪类型不符合核销要求：" + id);
        }
        return material;
    }

    private Map<Long, Material> loadMaterials(java.util.Collection<MaterialDocumentLine> lines) {
        Set<Long> ids = lines.stream().map(MaterialDocumentLine::getMaterialId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : materialMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private Map<String, MaterialDocumentLine> indexDocumentLines(List<MaterialDocumentLine> lines) {
        Map<String, MaterialDocumentLine> result = new HashMap<>();
        for (MaterialDocumentLine line : lines) {
            if (result.putIfAbsent(lineKey(line.getMaterialId(), line.getFixedAssetId(),
                    line.getSourceMaterialDocumentLineId()), line) != null) {
                throw conflict("核销单存在重复物料或资产行");
            }
        }
        return result;
    }

    private MaterialDocumentLine requireMatchingLine(MaterialSettlement settlement,
                                                     Map<String, MaterialDocumentLine> linesByKey) {
        MaterialDocumentLine line = linesByKey.get(lineKey(settlement.getMaterialId(), settlement.getFixedAssetId(),
                settlement.getSourceMaterialDocumentLineId()));
        if (line == null) {
            throw conflict("核销明细缺少对应单据行");
        }
        return line;
    }

    private void requireQuantityMatch(MaterialSettlement settlement, MaterialDocumentLine line) {
        quantityValidator.requirePositiveInteger(settlement.getQuantity(), "核销数量");
        quantityValidator.requirePositiveInteger(line.getQuantity(), "核销单据行数量");
        if (settlement.getQuantity().compareTo(line.getQuantity()) != 0) {
            throw conflict("核销明细与单据行数量不一致");
        }
    }

    private String lineKey(Long materialId, Long assetId, Long sourceLineId) {
        return materialId + ":" + (assetId == null ? "" : assetId)
                + ":" + (sourceLineId == null ? "" : sourceLineId);
    }

    private AssetStatus parseTargetStatus(String value) {
        if (value == null) {
            throw conflict("固定资产核销缺少目标状态");
        }
        try {
            AssetStatus status = AssetStatus.valueOf(value);
            if (status == AssetStatus.ON_LOAN || status == AssetStatus.IN_TRANSIT) {
                throw conflict("核销目标状态不得为ON_LOAN/IN_TRANSIT");
            }
            return status;
        } catch (IllegalArgumentException e) {
            throw conflict("固定资产目标状态不合法");
        }
    }

    private String movementKey(Long documentId, Long settlementId, String direction) {
        return documentId + ":" + settlementId + ":" + direction;
    }

    private String normalizePosition(String value) {
        String position = trimToNull(value);
        if (position != null && position.length() > 256) {
            throw badRequest("库内位置长度不能超过256");
        }
        return position;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trimToNull(String value) { return hasText(value) ? value.trim() : null; }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ResultCode.CONFLICT.getCode(), message);
    }

    private record CreateLine(Material material, Long assetId, BigDecimal quantity, String positionText,
                              String targetAssetStatus, String reason) {
    }

    private record BalanceKey(Long materialId, String positionText) {
    }

    private record ReviewResources(Map<BalanceKey, StockBalance> balances, Map<Long, FixedAsset> assets) {
    }
}
