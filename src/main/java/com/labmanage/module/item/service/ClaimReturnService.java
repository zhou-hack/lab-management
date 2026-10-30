package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.item.dto.ClaimReturnCreateDTO;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.entity.MaterialDocument;
import com.labmanage.module.item.entity.MaterialDocumentLine;
import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.AssetStatus;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper.SourceLineProcessing;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.vo.ClaimReturnContextVO;
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
public class ClaimReturnService {

    private static final String TYPE_OUTBOUND = "OUTBOUND";
    private static final String TYPE_INBOUND = "INBOUND";
    private static final String SCENE_ISSUE = "CLAIM_ISSUE";
    private static final String SCENE_RETURN = "CLAIM_RETURN";
    private static final String SHARED_DOCUMENT_TYPE = "ITEM_CLAIM_RETURN";
    private static final long DEFAULT_DEPARTMENT_ID = 1L;

    private final MaterialClaimMapper claimMapper;
    private final MaterialDocumentMapper documentMapper;
    private final MaterialDocumentLineMapper documentLineMapper;
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
    private final MaterialQuantityValidator quantityValidator;

    public ClaimReturnService(MaterialClaimMapper claimMapper,
                              MaterialDocumentMapper documentMapper,
                              MaterialDocumentLineMapper documentLineMapper,
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
                              MaterialQuantityValidator quantityValidator) {
        this.claimMapper = claimMapper;
        this.documentMapper = documentMapper;
        this.documentLineMapper = documentLineMapper;
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
        this.quantityValidator = quantityValidator;
    }

    public ClaimReturnContextVO returnContext(Long sourceId) {
        MaterialDocument source = requireSourceDocument(sourceId);
        MaterialClaim claim = requireClaim(source.getBusinessChainId());
        StockLocation location = requireLocation(source.getLocationId());
        List<MaterialDocumentLine> sourceLines = sourceLines(sourceId, false);
        return buildContext(source, claim, location, sourceLines, SecurityUtils.currentUserId(), timeService.now());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StockDocumentDetailVO returns(Long sourceId, ClaimReturnCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        MaterialDocument snapshot = requireSourceDocument(sourceId);
        MaterialClaim claim = requireClaimForUpdate(snapshot.getBusinessChainId());
        MaterialDocument source = documentMapper.selectByIdForUpdate(sourceId);
        requireSourceDocument(source);
        if (!claim.getId().equals(source.getBusinessChainId())) {
            throw conflict("来源出库单业务链已变化");
        }
        StockLocation location = requireLocation(source.getLocationId());
        locationAccessService.requireLocationAdmin(operatorId, location.getId());

        String idem = documentService.normalizeIdempotencyKey(dto.idempotencyKey());
        MaterialDocument existing = documentMapper.selectByOperatorTypeIdempotencyForUpdate(
                operatorId, TYPE_INBOUND, idem);
        if (existing != null) {
            if (!SCENE_RETURN.equals(existing.getBusinessScene()) || !sourceId.equals(existing.getSourceMaterialDocumentId())
                    || !claim.getId().equals(existing.getBusinessChainId())) {
                throw conflict("该幂等键已用于其他入库单");
            }
            return documentService.detail(existing.getId());
        }

        List<MaterialDocumentLine> sourceLines = sourceLines(sourceId, true);
        Map<Long, SourceLineProcessing> processing = processingByLineId(sourceId);
        Map<Long, Material> materials = loadMaterials(sourceLines);
        List<ResolvedReturn> resolved = resolveLines(dto.lines(), sourceLines, processing, materials);
        Map<Long, FixedAsset> lockedAssets = lockAndValidateResources(resolved, source.getLocationId());

        Instant postedAt = timeService.now();
        boolean overdue = postedAt.isAfter(claim.getReturnDeadline());
        String overdueReason = trimToNull(dto.overdueReason());
        if (overdue && overdueReason == null) {
            throw badRequest("逾期归还必须填写overdueReason");
        }
        MaterialDocument returnDocument = createDocument(source, claim, operatorId, idem, dto.remark(),
                overdue ? "OVERDUE" : "ON_TIME", overdue ? overdueReason : null, postedAt);
        for (ResolvedReturn line : resolved) {
            postReturn(line, returnDocument, operatorId, postedAt, lockedAssets);
            postSettlement(line, "CONSUMED", line.consumedQuantity(), line.consumedReason(),
                    returnDocument, operatorId, postedAt, lockedAssets);
            postSettlement(line, "LOST", line.lostQuantity(), line.lostReason(),
                    returnDocument, operatorId, postedAt, lockedAssets);
            postSettlement(line, "DAMAGED", line.damagedQuantity(), line.damagedReason(),
                    returnDocument, operatorId, postedAt, lockedAssets);
        }
        recomputeSourceStatus(source, operatorId, postedAt);
        appendClaimEvent(claim, returnDocument, operatorId, dto.remark(), postedAt);
        appendStockEvent(returnDocument, operatorId, dto.remark(), postedAt);
        return documentService.detail(returnDocument.getId());
    }

    private ClaimReturnContextVO buildContext(MaterialDocument source,
                                               MaterialClaim claim,
                                               StockLocation location,
                                               List<MaterialDocumentLine> sourceLines,
                                               Long operatorId,
                                               Instant now) {
        Map<Long, SourceLineProcessing> processing = processingByLineId(source.getId());
        Map<Long, Material> materials = loadMaterialsIncludingDeleted(sourceLines);
        Set<Long> assetIds = sourceLines.stream().map(MaterialDocumentLine::getFixedAssetId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, FixedAsset> assets = assetIds.isEmpty() ? Map.of()
                : assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(FixedAsset::getId, Function.identity()));
        List<ClaimReturnContextVO.Line> lines = new ArrayList<>();
        boolean invalidAsset = false;
        for (MaterialDocumentLine sourceLine : sourceLines) {
            SourceLineProcessing lineProcessing = processing.get(sourceLine.getId());
            if (lineProcessing == null || lineProcessing.remainingQuantity().signum() <= 0) {
                continue;
            }
            Material material = materials.get(sourceLine.getMaterialId());
            FixedAsset asset = sourceLine.getFixedAssetId() == null ? null : assets.get(sourceLine.getFixedAssetId());
            if (material.getMaterialType() == MaterialType.FIXED_ASSET
                    && (asset == null || asset.getStatus() != AssetStatus.ON_LOAN
                    || !source.getLocationId().equals(asset.getLocationId()))) {
                invalidAsset = true;
            }
            lines.add(new ClaimReturnContextVO.Line(str(sourceLine.getId()), str(sourceLine.getClaimLineId()),
                    str(material.getId()), material.getMaterialName(), material.getSpecification(),
                    material.getMaterialType(), material.getBaseUnit(), str(sourceLine.getFixedAssetId()),
                    asset == null ? null : asset.getAssetCode(), sourceLine.getPositionText(), sourceLine.getQuantity(),
                    lineProcessing.returnedQuantity(), lineProcessing.settledQuantity(),
                    lineProcessing.remainingQuantity()));
        }
        String blockedReason = null;
        if (!locationAccessService.isLocationAdmin(operatorId, source.getLocationId())) {
            blockedReason = "当前用户不是来源出库库位管理员";
        } else if (lines.isEmpty()) {
            blockedReason = "来源出库单已全部归还或核销";
        } else if (invalidAsset) {
            blockedReason = "待归还固定资产状态或库位已变化";
        }
        boolean overdue = now.isAfter(claim.getReturnDeadline());
        return new ClaimReturnContextVO(str(source.getId()), source.getDocumentNo(), str(claim.getId()),
                str(source.getBusinessChainId()), str(source.getLocationId()), location.getLocationCode(),
                location.getLocationName(), claim.getReturnDeadline(), overdue, blockedReason == null,
                blockedReason, lines);
    }

    private List<ResolvedReturn> resolveLines(List<ClaimReturnCreateDTO.Line> inputLines,
                                              List<MaterialDocumentLine> sourceLines,
                                              Map<Long, SourceLineProcessing> processing,
                                              Map<Long, Material> materials) {
        Map<Long, MaterialDocumentLine> sourceById = sourceLines.stream()
                .collect(Collectors.toMap(MaterialDocumentLine::getId, Function.identity()));
        Set<Long> usedSourceLines = new HashSet<>();
        List<ResolvedReturn> resolved = new ArrayList<>();
        for (ClaimReturnCreateDTO.Line input : inputLines) {
            Long sourceLineId = Ids.parse(input.sourceMaterialDocumentLineId());
            MaterialDocumentLine sourceLine = sourceById.get(sourceLineId);
            if (sourceLine == null) {
                throw badRequest("归还行必须引用来源出库单的原始行：" + sourceLineId);
            }
            if (!usedSourceLines.add(sourceLineId)) {
                throw badRequest("同一来源出库行在一次归还中只能出现一次");
            }
            SourceLineProcessing lineProcessing = processing.get(sourceLineId);
            if (lineProcessing == null) {
                throw conflict("来源出库行待处理量不存在");
            }
            Material material = materials.get(sourceLine.getMaterialId());
            BigDecimal returnQuantity = quantity(input.returnQuantity());
            BigDecimal consumedQuantity = quantity(input.consumedQuantity());
            BigDecimal lostQuantity = quantity(input.lostQuantity());
            BigDecimal damagedQuantity = quantity(input.damagedQuantity());
            quantityValidator.requireNonNegativeInteger(returnQuantity, "回库数量");
            quantityValidator.requireNonNegativeInteger(consumedQuantity, "教学消耗数量");
            quantityValidator.requireNonNegativeInteger(lostQuantity, "丢失数量");
            quantityValidator.requireNonNegativeInteger(damagedQuantity, "损坏数量");
            BigDecimal total = returnQuantity.add(consumedQuantity).add(lostQuantity).add(damagedQuantity);
            if (total.signum() <= 0) {
                throw badRequest("每条归还处理行的四类数量合计必须大于0");
            }
            if (total.compareTo(lineProcessing.remainingQuantity()) > 0) {
                throw conflict("归还及核销数量不能超过来源行剩余待处理量：" + sourceLineId);
            }
            if (material.getMaterialType() == MaterialType.FIXED_ASSET) {
                if (sourceLine.getFixedAssetId() == null) {
                    throw conflict("固定资产来源出库行缺少资产引用");
                }
                if (consumedQuantity.signum() > 0) {
                    throw badRequest("CONSUMED仅适用于耗材，固定资产不能教学消耗");
                }
                if (total.compareTo(BigDecimal.ONE) != 0 || lineProcessing.remainingQuantity().compareTo(BigDecimal.ONE) != 0
                        || !isInteger(returnQuantity) || !isInteger(lostQuantity) || !isInteger(damagedQuantity)) {
                    throw badRequest("固定资产回库、丢失、损坏数量合计只能为1");
                }
            } else if (sourceLine.getFixedAssetId() != null) {
                throw conflict("耗材来源出库行不能引用固定资产");
            }
            String consumedReason = requiredReason(consumedQuantity, input.consumedReason(), "教学消耗原因");
            String lostReason = requiredReason(lostQuantity, input.lostReason(), "丢失原因");
            String damagedReason = requiredReason(damagedQuantity, input.damagedReason(), "损坏原因");
            String positionText = input.positionText() == null || input.positionText().isBlank()
                    ? sourceLine.getPositionText() : normalizePosition(input.positionText());
            resolved.add(new ResolvedReturn(sourceLine, material, returnQuantity, consumedQuantity,
                    lostQuantity, damagedQuantity, consumedReason, lostReason, damagedReason, positionText));
        }
        resolved.sort(Comparator.comparing((ResolvedReturn line) -> line.material().getId())
                .thenComparing(line -> line.sourceLine().getId()));
        return resolved;
    }

    private Map<Long, FixedAsset> lockAndValidateResources(List<ResolvedReturn> lines, Long locationId) {
        Map<BalanceKey, StockBalance> balances = new HashMap<>();
        lines.stream().filter(line -> line.material().getMaterialType() == MaterialType.CONSUMABLE)
                .sorted(Comparator.comparing((ResolvedReturn line) -> line.material().getId())
                        .thenComparing(line -> line.positionText(), Comparator.nullsFirst(String::compareTo)))
                .forEach(line -> {
                    BalanceKey key = new BalanceKey(line.material().getId(), line.positionText());
                    if (!balances.containsKey(key)) {
                        StockBalance balance = balanceMapper.selectForUpdate(key.materialId(), locationId,
                                key.positionText());
                        if (balance == null) {
                            throw conflict("来源库位对应position的耗材余额不存在");
                        }
                        balances.put(key, balance);
                    }
                });
        validateBalanceTotals(lines, balances);

        Map<Long, FixedAsset> assets = new HashMap<>();
        lines.stream().filter(line -> line.material().getMaterialType() == MaterialType.FIXED_ASSET)
                .sorted(Comparator.comparing((ResolvedReturn line) -> line.material().getId())
                        .thenComparing(line -> line.sourceLine().getFixedAssetId()))
                .forEach(line -> {
                    Long assetId = line.sourceLine().getFixedAssetId();
                    FixedAsset asset = assetMapper.selectByIdForUpdate(assetId);
                    if (asset == null || !line.material().getId().equals(asset.getMaterialId())) {
                        throw conflict("归还固定资产不存在或物料不一致：" + assetId);
                    }
                    if (asset.getStatus() != AssetStatus.ON_LOAN || !locationId.equals(asset.getLocationId())) {
                        throw conflict("归还固定资产必须为来源库位的ON_LOAN资产：" + asset.getAssetCode());
                    }
                    assets.put(assetId, asset);
                });
        return assets;
    }

    private void validateBalanceTotals(List<ResolvedReturn> lines, Map<BalanceKey, StockBalance> balances) {
        Map<BalanceKey, BigDecimal> returns = new HashMap<>();
        Map<BalanceKey, BigDecimal> settlements = new HashMap<>();
        for (ResolvedReturn line : lines) {
            if (line.material().getMaterialType() != MaterialType.CONSUMABLE) {
                continue;
            }
            BalanceKey key = new BalanceKey(line.material().getId(), line.positionText());
            returns.merge(key, line.returnQuantity(), BigDecimal::add);
            settlements.merge(key, line.consumedQuantity().add(line.lostQuantity()).add(line.damagedQuantity()),
                    BigDecimal::add);
        }
        for (Map.Entry<BalanceKey, StockBalance> entry : balances.entrySet()) {
            StockBalance balance = entry.getValue();
            BigDecimal afterOwned = balance.getOwnedQuantity()
                    .subtract(settlements.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            BigDecimal afterAvailable = balance.getAvailableQuantity()
                    .add(returns.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            if (afterOwned.signum() < 0 || afterAvailable.signum() < 0
                    || afterOwned.compareTo(afterAvailable) < 0) {
                throw conflict("归还及核销后耗材余额不合法，请核对来源库位和position");
            }
        }
    }

    private void postReturn(ResolvedReturn line, MaterialDocument document,
                            Long operatorId, Instant now, Map<Long, FixedAsset> lockedAssets) {
        if (line.returnQuantity().signum() <= 0) {
            return;
        }
        MaterialDocumentLine returnLine = new MaterialDocumentLine();
        returnLine.setId(IdWorker.getId());
        returnLine.setMaterialDocumentId(document.getId());
        returnLine.setClaimLineId(line.sourceLine().getClaimLineId());
        returnLine.setSourceMaterialDocumentLineId(line.sourceLine().getId());
        returnLine.setMaterialId(line.material().getId());
        returnLine.setFixedAssetId(line.sourceLine().getFixedAssetId());
        returnLine.setQuantity(line.returnQuantity());
        returnLine.setPositionText(line.positionText());
        documentLineMapper.insert(returnLine);
        if (line.material().getMaterialType() == MaterialType.CONSUMABLE) {
            postingService.postConsumable(new StockPostingService.ConsumablePosting(
                    line.material().getId(), document.getLocationId(), line.positionText(), line.returnQuantity(),
                    BigDecimal.ZERO, line.returnQuantity(), SCENE_RETURN, "IN", document.getId(), returnLine.getId(),
                    operatorId, now, movementKey(document.getId(), returnLine.getId(), "IN")));
        } else {
            FixedAsset asset = lockedAssets.get(line.sourceLine().getFixedAssetId());
            postingService.transitionAsset(new StockPostingService.AssetTransitionPosting(
                    asset.getId(), AssetStatus.ON_LOAN, document.getLocationId(), AssetStatus.IDLE,
                    document.getLocationId(), line.positionText(), document.getLocationId(), SCENE_RETURN, "IN",
                    document.getId(), returnLine.getId(), operatorId, now,
                    movementKey(document.getId(), returnLine.getId(), "IN")));
        }
    }

    private void postSettlement(ResolvedReturn line, String type, BigDecimal quantity, String reason,
                                MaterialDocument document, Long operatorId, Instant now,
                                Map<Long, FixedAsset> lockedAssets) {
        if (quantity.signum() <= 0) {
            return;
        }
        MaterialSettlement settlement = new MaterialSettlement();
        settlement.setId(IdWorker.getId());
        settlement.setMaterialDocumentId(document.getId());
        settlement.setSourceMaterialDocumentLineId(line.sourceLine().getId());
        settlement.setMaterialId(line.material().getId());
        settlement.setFixedAssetId(line.sourceLine().getFixedAssetId());
        settlement.setSettlementType(type);
        settlement.setQuantity(quantity);
        settlement.setReason(reason);
        AssetStatus targetAssetStatus = null;
        if (line.material().getMaterialType() == MaterialType.FIXED_ASSET) {
            targetAssetStatus = "LOST".equals(type) ? AssetStatus.LOST : AssetStatus.MAINTENANCE;
            settlement.setTargetAssetStatus(targetAssetStatus.name());
        }
        settlementMapper.insert(settlement);
        if (line.material().getMaterialType() == MaterialType.CONSUMABLE) {
            postingService.postConsumable(new StockPostingService.ConsumablePosting(
                    line.material().getId(), document.getLocationId(), line.positionText(), quantity,
                    quantity.negate(), BigDecimal.ZERO, SCENE_RETURN + "_" + type, "OUT", document.getId(), null,
                    operatorId, now, movementKey(document.getId(), settlement.getId(), "OUT")));
        } else {
            FixedAsset asset = lockedAssets.get(line.sourceLine().getFixedAssetId());
            postingService.transitionAsset(new StockPostingService.AssetTransitionPosting(
                    asset.getId(), AssetStatus.ON_LOAN, document.getLocationId(), targetAssetStatus,
                    document.getLocationId(), asset.getPositionText(), document.getLocationId(),
                    SCENE_RETURN + "_" + type, "OUT", document.getId(), null, operatorId, now,
                    movementKey(document.getId(), settlement.getId(), "OUT")));
        }
    }

    private MaterialDocument createDocument(MaterialDocument source, MaterialClaim claim, Long operatorId,
                                            String idem, String remark, String returnStatus, String overdueReason,
                                            Instant now) {
        Long documentId = IdWorker.getId();
        String documentNo = "IM" + documentId;
        SysUser operator = userMapper.selectById(operatorId);
        BizDocument shared = new BizDocument();
        shared.setId(IdWorker.getId());
        shared.setDocumentNo(documentNo);
        shared.setDocumentType(SHARED_DOCUMENT_TYPE);
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
        document.setBusinessChainId(claim.getId());
        document.setDocumentType(TYPE_INBOUND);
        document.setBusinessScene(SCENE_RETURN);
        document.setDocumentSeq(documentMapper.selectNextDocumentSeq(claim.getId(), TYPE_INBOUND));
        document.setSourceMaterialDocumentId(source.getId());
        document.setLocationId(source.getLocationId());
        document.setOperatorId(operatorId);
        document.setResponsibleTeacherId(claim.getApplicantId());
        document.setPickupUserId(source.getPickupUserId());
        document.setOccurredAt(now);
        document.setPostedAt(now);
        document.setReturnStatus(returnStatus);
        document.setOverdueReason(overdueReason);
        document.setIdempotencyKey(idem);
        document.setVersion(0);
        document.setRemark(trimToNull(remark));
        try {
            documentMapper.insert(document);
        } catch (DuplicateKeyException e) {
            throw conflict("归还单编号、序号或幂等键冲突，请重试");
        }
        return document;
    }

    private void recomputeSourceStatus(MaterialDocument source, Long operatorId, Instant now) {
        List<SourceLineProcessing> processing = documentLineMapper.selectProcessingBySourceDocument(source.getId());
        if (processing.isEmpty() || processing.stream().anyMatch(line -> line.remainingQuantity().signum() < 0)) {
            throw conflict("来源出库行剩余量异常，不能完成归还");
        }
        boolean processed = processing.stream().allMatch(line -> line.remainingQuantity().signum() == 0);
        if (documentMapper.updateProcessStatus(source.getId(), source.getVersion(),
                processed ? "PROCESSED" : "PARTIAL", now, operatorId) != 1) {
            throw conflict("来源出库单处理状态已变化，请刷新后重试");
        }
    }

    private void appendClaimEvent(MaterialClaim claim, MaterialDocument returnDocument, Long operatorId,
                                  String remark, Instant now) {
        DocumentEvent event = baseEvent(claim.getBizDocumentId(), "RETURN", operatorId, now, remark);
        event.setFromStatus(claim.getStatus().name());
        event.setToStatus(claim.getStatus().name());
        event.setRequestKey("ITEM_CLAIM:" + claim.getId() + ":RETURN:" + returnDocument.getId());
        flowService.appendEvent(event);
    }

    private void appendStockEvent(MaterialDocument document, Long operatorId, String remark, Instant now) {
        DocumentEvent event = baseEvent(document.getBizDocumentId(), "STOCK_IN", operatorId, now, remark);
        event.setToStatus("POSTED");
        event.setRequestKey("STOCK_IN:" + document.getId());
        flowService.appendEvent(event);
    }

    private DocumentEvent baseEvent(Long documentId, String type, Long operatorId, Instant now, String reason) {
        DocumentEvent event = new DocumentEvent();
        event.setId(IdWorker.getId());
        event.setDocumentId(documentId);
        event.setEventType(type);
        event.setActorId(operatorId);
        event.setActorType("USER");
        event.setOccurredAt(now);
        event.setReason(trimToNull(reason));
        return event;
    }

    private MaterialDocument requireSourceDocument(Long sourceId) {
        MaterialDocument source = documentMapper.selectById(sourceId);
        return requireSourceDocument(source);
    }

    private MaterialDocument requireSourceDocument(MaterialDocument source) {
        if (source == null) {
            throw notFound("来源出库单不存在");
        }
        if (!TYPE_OUTBOUND.equals(source.getDocumentType()) || !SCENE_ISSUE.equals(source.getBusinessScene())
                || source.getPostedAt() == null || source.getBusinessChainId() == null || source.getLocationId() == null) {
            throw conflict("来源单据不是已过账的申领实发出库单");
        }
        return source;
    }

    private MaterialClaim requireClaim(Long id) {
        MaterialClaim claim = claimMapper.selectById(id);
        if (claim == null) {
            throw notFound("来源申领单不存在");
        }
        return claim;
    }

    private MaterialClaim requireClaimForUpdate(Long id) {
        MaterialClaim claim = claimMapper.selectByIdForUpdate(id);
        if (claim == null) {
            throw notFound("来源申领单不存在");
        }
        return claim;
    }

    private StockLocation requireLocation(Long id) {
        StockLocation location = locationMapper.selectById(id);
        if (location == null) {
            throw notFound("来源出库库位不存在");
        }
        return location;
    }

    private List<MaterialDocumentLine> sourceLines(Long sourceId, boolean forUpdate) {
        if (forUpdate) {
            return documentLineMapper.selectByDocumentIdForUpdate(sourceId);
        }
        return documentLineMapper.selectList(Wrappers.<MaterialDocumentLine>lambdaQuery()
                .eq(MaterialDocumentLine::getMaterialDocumentId, sourceId)
                .orderByAsc(MaterialDocumentLine::getId));
    }

    private Map<Long, SourceLineProcessing> processingByLineId(Long sourceId) {
        return documentLineMapper.selectProcessingBySourceDocument(sourceId).stream()
                .collect(Collectors.toMap(SourceLineProcessing::sourceLineId, Function.identity()));
    }

    private Map<Long, Material> loadMaterials(List<MaterialDocumentLine> lines) {
        Set<Long> ids = lines.stream().map(MaterialDocumentLine::getMaterialId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : materialMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private Map<Long, Material> loadMaterialsIncludingDeleted(List<MaterialDocumentLine> lines) {
        Set<Long> ids = lines.stream().map(MaterialDocumentLine::getMaterialId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : materialMapper.selectBatchIdsIncludingDeleted(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private BigDecimal quantity(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isInteger(BigDecimal value) {
        return value.stripTrailingZeros().scale() <= 0;
    }

    private String requiredReason(BigDecimal quantity, String reason, String label) {
        if (quantity.signum() <= 0) {
            return null;
        }
        String normalized = trimToNull(reason);
        if (normalized == null) {
            throw badRequest(label + "不能为空");
        }
        return normalized;
    }

    private String normalizePosition(String value) {
        String position = trimToNull(value);
        if (position != null && position.length() > 256) {
            throw badRequest("库内位置长度不能超过256");
        }
        return position;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String movementKey(Long documentId, Long lineId, String direction) {
        return documentId + ":" + lineId + ":" + direction;
    }

    private String str(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ResultCode.CONFLICT.getCode(), message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ResultCode.NOT_FOUND.getCode(), message);
    }

    private record BalanceKey(Long materialId, String positionText) {
    }

    private record ResolvedReturn(
            MaterialDocumentLine sourceLine,
            Material material,
            BigDecimal returnQuantity,
            BigDecimal consumedQuantity,
            BigDecimal lostQuantity,
            BigDecimal damagedQuantity,
            String consumedReason,
            String lostReason,
            String damagedReason,
            String positionText
    ) {
    }
}
