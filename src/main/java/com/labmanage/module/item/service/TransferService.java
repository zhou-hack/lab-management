package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.document.entity.ApprovalTask;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.item.dto.TransferCreateDTO;
import com.labmanage.module.item.dto.TransferPageQuery;
import com.labmanage.module.item.dto.TransferReceiptCreateDTO;
import com.labmanage.module.item.dto.TransferReturnRequestDTO;
import com.labmanage.module.item.dto.TransferWriteoffCreateDTO;
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
import com.labmanage.module.item.vo.ItemDocumentEventVO;
import com.labmanage.module.item.vo.TransferDetailVO;
import com.labmanage.module.item.vo.TransferSummaryVO;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransferService {

    public static final String TYPE_TRANSFER = "TRANSFER";
    public static final String TYPE_RECEIPT = "TRANSFER_RECEIPT";
    public static final String TYPE_RETURN = "TRANSFER_RETURN";
    public static final String TYPE_SETTLEMENT = "TRANSFER_SETTLEMENT";
    public static final String SCENE_SEND = "TRANSFER_SEND";
    public static final String SCENE_RECEIVE = "TRANSFER_RECEIVE";
    public static final String SCENE_RETURN = "TRANSFER_RETURN";
    public static final String SCENE_SETTLE = "TRANSFER_SETTLE";
    public static final String REVIEW_STAGE = "ITEM_STOCK_REVIEW";

    private static final long DEFAULT_DEPARTMENT_ID = 1L;
    private static final Set<String> OPEN_STATUSES = Set.of("PENDING_RECEIPT", "PARTIALLY_RECEIVED");
    private static final Set<String> TRANSFER_STATUSES = Set.of(
            "PENDING_RECEIPT", "PARTIALLY_RECEIVED", "PENDING_RETURN", "CLOSED");
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "documentNo", "document_no",
            "occurredAt", "occurred_at",
            "createTime", "create_time",
            "transferStatus", "transfer_status"
    );

    private final MaterialDocumentMapper documentMapper;
    private final MaterialDocumentLineMapper lineMapper;
    private final MaterialSettlementMapper settlementMapper;
    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final StockBalanceMapper balanceMapper;
    private final FixedAssetMapper assetMapper;
    private final SysUserMapper userMapper;
    private final StockPostingService postingService;
    private final ItemFlowService flowService;
    private final ItemTimeService timeService;
    private final ItemLocationAccessService locationAccessService;
    private final StockDocumentService documentService;
    private final MaterialQuantityValidator quantityValidator;

    public TransferService(MaterialDocumentMapper documentMapper,
                           MaterialDocumentLineMapper lineMapper,
                           MaterialSettlementMapper settlementMapper,
                           MaterialMapper materialMapper,
                           StockLocationMapper locationMapper,
                           StockBalanceMapper balanceMapper,
                           FixedAssetMapper assetMapper,
                           SysUserMapper userMapper,
                           StockPostingService postingService,
                           ItemFlowService flowService,
                           ItemTimeService timeService,
                           ItemLocationAccessService locationAccessService,
                           StockDocumentService documentService,
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
        this.flowService = flowService;
        this.timeService = timeService;
        this.locationAccessService = locationAccessService;
        this.documentService = documentService;
        this.quantityValidator = quantityValidator;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TransferDetailVO create(TransferCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        Long fromLocationId = Ids.parse(dto.fromLocationId());
        Long toLocationId = Ids.parse(dto.toLocationId());
        if (fromLocationId.equals(toLocationId)) {
            throw badRequest("源库位与目标库位不能相同");
        }
        Map<Long, StockLocation> locations = lockLocations(fromLocationId, toLocationId);
        requireEnabled(locations.get(fromLocationId));
        requireEnabled(locations.get(toLocationId));
        locationAccessService.requireLocationAdmin(operatorId, fromLocationId);

        String idem = documentService.normalizeIdempotencyKey(dto.idempotencyKey());
        MaterialDocument existing = findByIdempotency(operatorId, TYPE_TRANSFER, idem);
        if (existing != null) {
            if (!SCENE_SEND.equals(existing.getBusinessScene())
                    || !fromLocationId.equals(existing.getFromLocationId())
                    || !toLocationId.equals(existing.getToLocationId())) {
                throw conflict("该幂等键已用于其他移动单");
            }
            return detail(existing.getId());
        }

        List<CreateLine> resolved = resolveCreateLines(dto.lines());
        lockAndValidateCreateStock(resolved, fromLocationId);
        Instant now = timeService.now();
        MaterialDocument document = createDocument(TYPE_TRANSFER, SCENE_SEND, operatorId, idem, null,
                fromLocationId, toLocationId, null, now, now, null, false, dto.remark());
        for (CreateLine line : resolved) {
            MaterialDocumentLine sourceLine = insertLine(document.getId(), null, line.material().getId(),
                    line.assetId(), line.quantity(), line.positionText());
            if (line.material().getMaterialType() == MaterialType.CONSUMABLE) {
                postingService.postConsumable(new StockPostingService.ConsumablePosting(
                        line.material().getId(), fromLocationId, line.positionText(), line.quantity(),
                        line.quantity().negate(), line.quantity().negate(), SCENE_SEND, "OUT",
                        document.getId(), sourceLine.getId(), operatorId, now,
                        movementKey(document.getId(), sourceLine.getId(), "OUT")));
            } else {
                postingService.transitionAsset(new StockPostingService.AssetTransitionPosting(
                        line.assetId(), AssetStatus.IDLE, fromLocationId, AssetStatus.IN_TRANSIT,
                        fromLocationId, line.positionText(), fromLocationId, SCENE_SEND, "OUT",
                        document.getId(), sourceLine.getId(), operatorId, now,
                        movementKey(document.getId(), sourceLine.getId(), "OUT")));
            }
        }
        appendEvent(document.getBizDocumentId(), "TRANSFER_SEND", null, "PENDING_RECEIPT",
                operatorId, now, dto.remark(), "TRANSFER_SEND:" + document.getId(), null);
        return detail(document.getId());
    }

    public PageResult<TransferSummaryVO> page(TransferPageQuery query) {
        Long userId = SecurityUtils.currentUserId();
        List<Long> locationIds = locationAccessService.userLocationIds(userId);
        if (locationIds.isEmpty()) {
            return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
        }
        String status = trimToNull(query.getTransferStatus());
        if (status != null && !TRANSFER_STATUSES.contains(status)) {
            throw badRequest("移动状态不合法");
        }
        LambdaQueryWrapper<MaterialDocument> wrapper = Wrappers.<MaterialDocument>lambdaQuery()
                .eq(MaterialDocument::getDocumentType, TYPE_TRANSFER)
                .eq(MaterialDocument::getBusinessScene, SCENE_SEND)
                .isNotNull(MaterialDocument::getPostedAt)
                .eq(query.getFromLocationId() != null,
                        MaterialDocument::getFromLocationId, query.getFromLocationId())
                .eq(query.getToLocationId() != null,
                        MaterialDocument::getToLocationId, query.getToLocationId())
                .eq(status != null, MaterialDocument::getTransferStatus, status)
                .and(access -> access.in(MaterialDocument::getFromLocationId, locationIds)
                        .or().in(MaterialDocument::getToLocationId, locationIds));
        String sortColumn = query.getSortBy() == null ? null : SORT_COLUMNS.get(query.getSortBy());
        if (sortColumn == null) {
            wrapper.orderByDesc(MaterialDocument::getOccurredAt).orderByDesc(MaterialDocument::getId);
        } else {
            wrapper.last("ORDER BY " + sortColumn
                    + ("asc".equalsIgnoreCase(query.getSortOrder()) ? " ASC" : " DESC") + ", id DESC");
        }
        Page<MaterialDocument> page = documentMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<MaterialDocument> records = page.getRecords();
        Map<Long, StockLocation> locations = loadLocations(records);
        Map<Long, SysUser> users = loadOperators(records);
        List<TransferSummaryVO> vos = records.stream()
                .map(document -> toSummary(document, locations, users)).toList();
        return PageResult.of(vos, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    public TransferDetailVO detail(Long id) {
        MaterialDocument original = requireTransfer(id);
        Long userId = SecurityUtils.currentUserId();
        boolean sourceAdmin = locationAccessService.isLocationAdmin(userId, original.getFromLocationId());
        boolean targetAdmin = locationAccessService.isLocationAdmin(userId, original.getToLocationId());
        if (!sourceAdmin && !targetAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅源库位或目标库位管理员可查看移动单");
        }
        List<MaterialDocumentLine> sourceLines = sourceLines(id, false);
        Map<Long, TransferLineProcessing> processing = processingByLineId(id);
        Map<Long, Material> materials = loadMaterialsIncludingDeleted(sourceLines);
        Map<Long, FixedAsset> assets = loadAssets(sourceLines);
        List<TransferDetailVO.Line> lines = new ArrayList<>();
        BigDecimal totalRemaining = BigDecimal.ZERO;
        boolean hasRemaining = false;
        for (MaterialDocumentLine sourceLine : sourceLines) {
            TransferLineProcessing amounts = processing.get(sourceLine.getId());
            if (amounts == null || amounts.remainingQuantity().signum() < 0) {
                throw conflict("原移动行待处理量异常");
            }
            Material material = materials.get(sourceLine.getMaterialId());
            FixedAsset asset = sourceLine.getFixedAssetId() == null ? null : assets.get(sourceLine.getFixedAssetId());
            lines.add(new TransferDetailVO.Line(str(sourceLine.getId()), str(sourceLine.getMaterialId()),
                    material == null ? null : material.getMaterialName(),
                    material == null ? null : material.getSpecification(),
                    material == null ? null : material.getBaseUnit(), str(sourceLine.getFixedAssetId()),
                    asset == null ? null : asset.getAssetCode(), sourceLine.getPositionText(),
                    sourceLine.getQuantity(), amounts.receivedQuantity(), amounts.returnedQuantity(),
                    amounts.settledQuantity(), amounts.remainingQuantity()));
            totalRemaining = totalRemaining.add(amounts.remainingQuantity());
            hasRemaining |= amounts.remainingQuantity().signum() > 0;
        }
        List<MaterialDocument> children = childDocuments(id);
        List<ItemDocumentEventVO> events = sharedEvents(original, children);
        boolean open = OPEN_STATUSES.contains(original.getTransferStatus()) && hasRemaining;
        boolean canReceive = open && targetAdmin && SecurityUtils.hasPermission("item:transfer:receive");
        boolean canReturnRequest = canReceive;
        boolean canReturnReceive = "PENDING_RETURN".equals(original.getTransferStatus()) && hasRemaining
                && sourceAdmin && SecurityUtils.hasPermission("item:transfer:receive");
        boolean canWriteoff = (open || "PENDING_RETURN".equals(original.getTransferStatus()) && hasRemaining)
                && sourceAdmin && userId.equals(original.getOperatorId())
                && SecurityUtils.hasPermission("item:settlement:create");
        boolean canReview = sourceAdmin && SecurityUtils.hasPermission("item:settlement:review")
                && children.stream().anyMatch(child -> TYPE_SETTLEMENT.equals(child.getDocumentType())
                && "PENDING_REVIEW".equals(child.getReviewStatus())
                && !userId.equals(child.getOperatorId()));
        Map<Long, StockLocation> locations = loadLocations(List.of(original));
        Map<Long, SysUser> users = loadOperators(List.of(original));
        return new TransferDetailVO(toSummary(original, locations, users), totalRemaining, lines, events,
                canReceive, canReturnRequest, canReturnReceive, canWriteoff, canReview);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TransferDetailVO receive(Long id, TransferReceiptCreateDTO dto) {
        return processReceipt(id, dto, false);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TransferDetailVO returnReceipts(Long id, TransferReceiptCreateDTO dto) {
        return processReceipt(id, dto, true);
    }

    private TransferDetailVO processReceipt(Long id, TransferReceiptCreateDTO dto, boolean returning) {
        Long operatorId = SecurityUtils.currentUserId();
        requireTransfer(id);
        MaterialDocument original = documentMapper.selectByIdForUpdate(id);
        requireTransfer(original);
        String type = returning ? TYPE_RETURN : TYPE_RECEIPT;
        String scene = returning ? SCENE_RETURN : SCENE_RECEIVE;
        Map<Long, StockLocation> locations = lockLocations(original.getFromLocationId(), original.getToLocationId());
        Long operationLocationId = returning ? original.getFromLocationId() : original.getToLocationId();
        locationAccessService.requireLocationAdmin(operatorId, operationLocationId);
        if (!returning) {
            requireEnabled(locations.get(operationLocationId));
        }
        String idem = documentService.normalizeIdempotencyKey(dto.idempotencyKey());
        MaterialDocument existing = findByIdempotency(operatorId, type, idem);
        if (existing != null) {
            if (!id.equals(existing.getSourceMaterialDocumentId()) || !scene.equals(existing.getBusinessScene())) {
                throw conflict("该幂等键已用于其他移动子单");
            }
            return detail(id);
        }
        if (returning && !"PENDING_RETURN".equals(original.getTransferStatus())) {
            throw conflict("仅待退回移动单可确认退回实收");
        }
        if (!returning && !OPEN_STATUSES.contains(original.getTransferStatus())) {
            throw conflict("当前移动状态不可接收");
        }

        List<MaterialDocumentLine> sourceLines = sourceLines(id, true);
        Map<Long, TransferLineProcessing> processing = processingByLineId(id);
        Map<Long, Material> materials = loadMaterials(sourceLines);
        List<ProcessLine> resolved = resolveProcessLines(dto.lines(), sourceLines, processing, materials);
        lockAndValidateProcessStock(resolved, original, returning);
        Instant now = timeService.now();
        MaterialDocument child = createDocument(type, scene, operatorId, idem, original,
                original.getFromLocationId(), original.getToLocationId(), operationLocationId,
                now, now, null, true, dto.remark());
        for (ProcessLine line : resolved) {
            MaterialDocumentLine childLine = insertLine(child.getId(), line.sourceLine().getId(),
                    line.material().getId(), line.sourceLine().getFixedAssetId(), line.quantity(), line.positionText());
            if (line.material().getMaterialType() == MaterialType.CONSUMABLE) {
                postingService.postConsumable(new StockPostingService.ConsumablePosting(
                        line.material().getId(), operationLocationId, line.positionText(), line.quantity(),
                        line.quantity(), line.quantity(), scene, "IN", child.getId(), childLine.getId(),
                        operatorId, now, movementKey(child.getId(), childLine.getId(), "IN")));
            } else {
                postingService.transitionAsset(new StockPostingService.AssetTransitionPosting(
                        line.sourceLine().getFixedAssetId(), AssetStatus.IN_TRANSIT, original.getFromLocationId(),
                        AssetStatus.IDLE, operationLocationId, line.positionText(), operationLocationId,
                        scene, "IN", child.getId(), childLine.getId(), operatorId, now,
                        movementKey(child.getId(), childLine.getId(), "IN")));
            }
        }
        String previousStatus = original.getTransferStatus();
        String nextStatus = recomputeStatus(original, operatorId, now, operatorId, now);
        String eventType = returning ? "TRANSFER_RETURN" : "TRANSFER_RECEIVE";
        appendEvent(child.getBizDocumentId(), eventType, null, "POSTED", operatorId, now, dto.remark(),
                eventType + ":" + child.getId(), null);
        appendEvent(original.getBizDocumentId(), eventType, previousStatus, nextStatus, operatorId, now,
                dto.remark(), eventType + ":" + original.getId() + ":" + child.getId(), null);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TransferDetailVO returnRequest(Long id, TransferReturnRequestDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        requireTransfer(id);
        MaterialDocument original = documentMapper.selectByIdForUpdate(id);
        requireTransfer(original);
        locationAccessService.requireLocationAdmin(operatorId, original.getToLocationId());
        String idem = documentService.normalizeIdempotencyKey(dto.idempotencyKey());
        String requestKey = "TRANSFER_RETURN_REQUEST:" + id + ":" + idem;
        boolean repeated = flowService.listEvents(original.getBizDocumentId()).stream()
                .anyMatch(event -> requestKey.equals(event.getRequestKey()));
        if (repeated) {
            return detail(id);
        }
        if (!OPEN_STATUSES.contains(original.getTransferStatus())) {
            throw conflict("当前移动状态不可登记拒收");
        }
        sourceLines(id, true);
        if (processingByLineId(id).values().stream().noneMatch(line -> line.remainingQuantity().signum() > 0)) {
            throw conflict("移动单已无剩余在途数量");
        }
        String previousStatus = original.getTransferStatus();
        int updated = documentMapper.update(null, Wrappers.<MaterialDocument>lambdaUpdate()
                .eq(MaterialDocument::getId, id)
                .eq(MaterialDocument::getVersion, original.getVersion())
                .set(MaterialDocument::getTransferStatus, "PENDING_RETURN")
                .set(MaterialDocument::getVersion, original.getVersion() + 1)
                .set(MaterialDocument::getUpdateTime, timeService.now())
                .set(MaterialDocument::getUpdateUserId, operatorId));
        if (updated != 1) {
            throw conflict("移动单状态已变化，请刷新后重试");
        }
        Instant now = timeService.now();
        appendEvent(original.getBizDocumentId(), "TRANSFER_RETURN_REQUEST", previousStatus, "PENDING_RETURN",
                operatorId, now, dto.reason(), requestKey, null);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TransferDetailVO writeoffs(Long id, TransferWriteoffCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        requireTransfer(id);
        MaterialDocument original = documentMapper.selectByIdForUpdate(id);
        requireTransfer(original);
        if (!operatorId.equals(original.getOperatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "在途核销必须由原移动经办人发起");
        }
        locationAccessService.requireLocationAdmin(operatorId, original.getFromLocationId());
        String idem = documentService.normalizeIdempotencyKey(dto.idempotencyKey());
        MaterialDocument existing = findByIdempotency(operatorId, TYPE_SETTLEMENT, idem);
        if (existing != null) {
            if (!id.equals(existing.getSourceMaterialDocumentId())
                    || !SCENE_SETTLE.equals(existing.getBusinessScene())) {
                throw conflict("该幂等键已用于其他在途核销单");
            }
            return detail(id);
        }
        if (!OPEN_STATUSES.contains(original.getTransferStatus())
                && !"PENDING_RETURN".equals(original.getTransferStatus())) {
            throw conflict("当前移动状态不可登记在途核销");
        }

        List<MaterialDocumentLine> sourceLines = sourceLines(id, true);
        Map<Long, TransferLineProcessing> processing = processingByLineId(id);
        Map<Long, Material> materials = loadMaterials(sourceLines);
        List<WriteoffLine> resolved = resolveWriteoffLines(dto.lines(), sourceLines, processing, materials);
        lockAndValidateWriteoffAssets(resolved, original.getFromLocationId());
        Instant now = timeService.now();
        MaterialDocument child = createDocument(TYPE_SETTLEMENT, SCENE_SETTLE, operatorId, idem, original,
                original.getFromLocationId(), original.getToLocationId(), original.getFromLocationId(),
                now, null, "PENDING_REVIEW", false, dto.remark());
        for (WriteoffLine line : resolved) {
            insertLine(child.getId(), line.sourceLine().getId(), line.material().getId(),
                    line.sourceLine().getFixedAssetId(), line.quantity(), line.sourceLine().getPositionText());
            MaterialSettlement settlement = new MaterialSettlement();
            settlement.setId(IdWorker.getId());
            settlement.setMaterialDocumentId(child.getId());
            settlement.setSourceMaterialDocumentLineId(line.sourceLine().getId());
            settlement.setMaterialId(line.material().getId());
            settlement.setFixedAssetId(line.sourceLine().getFixedAssetId());
            settlement.setSettlementType(line.settlementType());
            settlement.setQuantity(line.quantity());
            if (line.material().getMaterialType() == MaterialType.FIXED_ASSET) {
                settlement.setTargetAssetStatus("LOST".equals(line.settlementType()) ? "LOST" : "SCRAPPED");
            }
            settlement.setReason(line.reason().trim());
            settlementMapper.insert(settlement);
        }
        createReviewTask(child, operatorId, now);
        appendEvent(child.getBizDocumentId(), "SETTLEMENT_SUBMIT", null, "PENDING_REVIEW",
                operatorId, now, dto.remark(), "SETTLEMENT_SUBMIT:" + child.getId(), null);
        appendEvent(original.getBizDocumentId(), "TRANSFER_SETTLEMENT_CREATE",
                original.getTransferStatus(), original.getTransferStatus(), operatorId, now, dto.remark(),
                "TRANSFER_SETTLEMENT_CREATE:" + original.getId() + ":" + child.getId(), null);
        return detail(id);
    }

    public String recomputeStatus(MaterialDocument original, Long operatorId, Instant now,
                                  Long receivedBy, Instant receivedAt) {
        List<TransferLineProcessing> processing = lineMapper.selectTransferProcessingBySourceDocument(original.getId());
        if (processing.isEmpty() || processing.stream().anyMatch(line -> line.remainingQuantity().signum() < 0)) {
            throw conflict("原移动行待处理量异常，不能完成处理");
        }
        boolean closed = processing.stream().allMatch(line -> line.remainingQuantity().signum() == 0);
        boolean processed = processing.stream().anyMatch(line -> line.receivedQuantity().signum() > 0
                || line.returnedQuantity().signum() > 0 || line.settledQuantity().signum() > 0);
        String nextStatus;
        if (closed) {
            nextStatus = "CLOSED";
        } else if ("PENDING_RETURN".equals(original.getTransferStatus())) {
            nextStatus = "PENDING_RETURN";
        } else {
            nextStatus = processed ? "PARTIALLY_RECEIVED" : "PENDING_RECEIPT";
        }
        var update = Wrappers.<MaterialDocument>lambdaUpdate()
                .eq(MaterialDocument::getId, original.getId())
                .eq(MaterialDocument::getVersion, original.getVersion())
                .set(MaterialDocument::getTransferStatus, nextStatus)
                .set(MaterialDocument::getVersion, original.getVersion() + 1)
                .set(MaterialDocument::getUpdateTime, now)
                .set(MaterialDocument::getUpdateUserId, operatorId)
                .set(receivedBy != null, MaterialDocument::getReceivedBy, receivedBy)
                .set(receivedAt != null, MaterialDocument::getReceivedAt, receivedAt);
        if (documentMapper.update(null, update) != 1) {
            throw conflict("移动单处理状态已变化，请刷新后重试");
        }
        original.setTransferStatus(nextStatus);
        original.setVersion(original.getVersion() + 1);
        return nextStatus;
    }

    private List<CreateLine> resolveCreateLines(List<TransferCreateDTO.Line> inputs) {
        List<CreateLine> result = new ArrayList<>();
        Set<Long> assetIds = new HashSet<>();
        Set<String> consumableKeys = new HashSet<>();
        for (TransferCreateDTO.Line input : inputs) {
            if (input.quantity() != null) {
                quantityValidator.requirePositiveInteger(input.quantity(), "移动数量");
            }
            if (hasText(input.assetId())) {
                Long assetId = Ids.parse(input.assetId());
                if (!assetIds.add(assetId)) {
                    throw badRequest("同一台固定资产在一次移动中只能出现一次");
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
                    throw badRequest("固定资产移动必须逐台且每行数量为1");
                }
                result.add(new CreateLine(material, assetId, quantity, asset.getPositionText()));
            } else {
                if (!hasText(input.materialId())) {
                    throw badRequest("耗材移动行必须填写materialId");
                }
                Long materialId = Ids.parse(input.materialId());
                Material material = requireMaterial(materialId, MaterialType.CONSUMABLE);
                if (input.quantity() == null || input.quantity().signum() <= 0) {
                    throw badRequest("耗材移动数量必须大于0");
                }
                String positionText = normalizePosition(input.positionText());
                if (!consumableKeys.add(materialId + ":" + (positionText == null ? "" : positionText))) {
                    throw badRequest("同一耗材及position在一次移动中只能出现一次");
                }
                result.add(new CreateLine(material, null, input.quantity(), positionText));
            }
        }
        result.sort(Comparator.comparing((CreateLine line) -> line.material().getId())
                .thenComparing(line -> line.assetId(), Comparator.nullsFirst(Long::compareTo))
                .thenComparing(line -> line.positionText(), Comparator.nullsFirst(String::compareTo)));
        return result;
    }

    private void lockAndValidateCreateStock(List<CreateLine> lines, Long fromLocationId) {
        for (CreateLine line : lines) {
            if (line.material().getMaterialType() == MaterialType.CONSUMABLE) {
                StockBalance balance = balanceMapper.selectForUpdate(line.material().getId(), fromLocationId,
                        line.positionText());
                if (balance == null || balance.getAvailableQuantity().compareTo(line.quantity()) < 0) {
                    throw conflict("源库位该position耗材可用量不足：" + line.material().getMaterialName());
                }
            } else {
                FixedAsset asset = assetMapper.selectByIdForUpdate(line.assetId());
                if (asset == null || !line.material().getId().equals(asset.getMaterialId())) {
                    throw conflict("移动固定资产不存在或物料不一致：" + line.assetId());
                }
                if (asset.getStatus() != AssetStatus.IDLE || !fromLocationId.equals(asset.getLocationId())) {
                    throw conflict("固定资产必须当前IDLE且位于源库位：" + asset.getAssetCode());
                }
            }
        }
    }

    private List<ProcessLine> resolveProcessLines(List<TransferReceiptCreateDTO.Line> inputs,
                                                  List<MaterialDocumentLine> sourceLines,
                                                  Map<Long, TransferLineProcessing> processing,
                                                  Map<Long, Material> materials) {
        Map<Long, MaterialDocumentLine> sourceById = sourceLines.stream()
                .collect(Collectors.toMap(MaterialDocumentLine::getId, Function.identity()));
        Set<Long> used = new HashSet<>();
        List<ProcessLine> result = new ArrayList<>();
        for (TransferReceiptCreateDTO.Line input : inputs) {
            quantityValidator.requirePositiveInteger(input.quantity(), "处理数量");
            Long sourceLineId = Ids.parse(input.sourceMaterialDocumentLineId());
            MaterialDocumentLine sourceLine = sourceById.get(sourceLineId);
            if (sourceLine == null) {
                throw badRequest("处理行必须引用原移动单的原始行：" + sourceLineId);
            }
            if (!used.add(sourceLineId)) {
                throw badRequest("同一原移动行在一次处理中只能出现一次");
            }
            TransferLineProcessing amounts = processing.get(sourceLineId);
            if (amounts == null || input.quantity().compareTo(amounts.remainingQuantity()) > 0) {
                throw conflict("处理数量不能超过原移动行剩余在途量：" + sourceLineId);
            }
            Material material = materials.get(sourceLine.getMaterialId());
            if (material.getMaterialType() == MaterialType.FIXED_ASSET
                    && (sourceLine.getFixedAssetId() == null || input.quantity().compareTo(BigDecimal.ONE) != 0
                    || amounts.remainingQuantity().compareTo(BigDecimal.ONE) != 0)) {
                throw badRequest("固定资产必须按原移动行整台处理，数量为1");
            }
            String positionText = hasText(input.positionText())
                    ? normalizePosition(input.positionText()) : sourceLine.getPositionText();
            result.add(new ProcessLine(sourceLine, material, input.quantity(), positionText));
        }
        result.sort(Comparator.comparing((ProcessLine line) -> line.material().getId())
                .thenComparing(line -> line.sourceLine().getFixedAssetId(), Comparator.nullsFirst(Long::compareTo))
                .thenComparing(line -> line.positionText(), Comparator.nullsFirst(String::compareTo))
                .thenComparing(line -> line.sourceLine().getId()));
        return result;
    }

    private void lockAndValidateProcessStock(List<ProcessLine> lines, MaterialDocument original, boolean returning) {
        for (ProcessLine line : lines) {
            if (line.material().getMaterialType() == MaterialType.FIXED_ASSET) {
                FixedAsset asset = assetMapper.selectByIdForUpdate(line.sourceLine().getFixedAssetId());
                if (asset == null || !line.material().getId().equals(asset.getMaterialId())
                        || asset.getStatus() != AssetStatus.IN_TRANSIT
                        || !original.getFromLocationId().equals(asset.getLocationId())) {
                    throw conflict("固定资产必须保持源库位IN_TRANSIT状态才能处理：" + line.sourceLine().getFixedAssetId());
                }
            } else if (returning) {
                StockBalance balance = balanceMapper.selectForUpdate(line.material().getId(),
                        original.getFromLocationId(), line.positionText());
                if (balance == null) {
                    throw conflict("源库位对应position的耗材余额不存在");
                }
            }
        }
    }

    private List<WriteoffLine> resolveWriteoffLines(List<TransferWriteoffCreateDTO.Line> inputs,
                                                    List<MaterialDocumentLine> sourceLines,
                                                    Map<Long, TransferLineProcessing> processing,
                                                    Map<Long, Material> materials) {
        Map<Long, MaterialDocumentLine> sourceById = sourceLines.stream()
                .collect(Collectors.toMap(MaterialDocumentLine::getId, Function.identity()));
        Set<Long> used = new HashSet<>();
        List<WriteoffLine> result = new ArrayList<>();
        for (TransferWriteoffCreateDTO.Line input : inputs) {
            quantityValidator.requirePositiveInteger(input.quantity(), "核销数量");
            Long sourceLineId = Ids.parse(input.sourceMaterialDocumentLineId());
            MaterialDocumentLine sourceLine = sourceById.get(sourceLineId);
            if (sourceLine == null) {
                throw badRequest("核销行必须引用原移动单的原始行：" + sourceLineId);
            }
            if (!used.add(sourceLineId)) {
                throw badRequest("同一原移动行在一次核销中只能出现一次");
            }
            TransferLineProcessing amounts = processing.get(sourceLineId);
            if (amounts == null || input.quantity().compareTo(amounts.remainingQuantity()) > 0) {
                throw conflict("核销数量不能超过原移动行剩余在途量：" + sourceLineId);
            }
            Material material = materials.get(sourceLine.getMaterialId());
            if (material.getMaterialType() == MaterialType.FIXED_ASSET
                    && (sourceLine.getFixedAssetId() == null || input.quantity().compareTo(BigDecimal.ONE) != 0
                    || amounts.remainingQuantity().compareTo(BigDecimal.ONE) != 0)) {
                throw badRequest("固定资产在途核销必须按原移动行整台处理，数量为1");
            }
            result.add(new WriteoffLine(sourceLine, material, input.quantity(),
                    input.settlementType(), input.reason()));
        }
        result.sort(Comparator.comparing((WriteoffLine line) -> line.material().getId())
                .thenComparing(line -> line.sourceLine().getFixedAssetId(), Comparator.nullsFirst(Long::compareTo))
                .thenComparing(line -> line.sourceLine().getId()));
        return result;
    }

    private void lockAndValidateWriteoffAssets(List<WriteoffLine> lines, Long sourceLocationId) {
        for (WriteoffLine line : lines) {
            if (line.material().getMaterialType() != MaterialType.FIXED_ASSET) {
                continue;
            }
            FixedAsset asset = assetMapper.selectByIdForUpdate(line.sourceLine().getFixedAssetId());
            if (asset == null || asset.getStatus() != AssetStatus.IN_TRANSIT
                    || !sourceLocationId.equals(asset.getLocationId())) {
                throw conflict("固定资产必须保持源库位IN_TRANSIT状态才能核销：" + line.sourceLine().getFixedAssetId());
            }
        }
    }

    private MaterialDocument createDocument(String type, String scene, Long operatorId, String idem,
                                            MaterialDocument source, Long fromLocationId, Long toLocationId,
                                            Long locationId, Instant now, Instant postedAt, String reviewStatus,
                                            boolean received, String remark) {
        Long documentId = IdWorker.getId();
        String documentNo = "IM" + documentId;
        SysUser operator = userMapper.selectById(operatorId);
        BizDocument shared = new BizDocument();
        shared.setId(IdWorker.getId());
        shared.setDocumentNo(documentNo);
        shared.setDocumentType("ITEM_" + scene);
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
        document.setBusinessScene(scene);
        document.setSourceMaterialDocumentId(source == null ? null : source.getId());
        document.setLocationId(locationId);
        document.setFromLocationId(fromLocationId);
        document.setToLocationId(toLocationId);
        document.setTransferStatus(source == null ? "PENDING_RECEIPT" : null);
        document.setReviewStatus(reviewStatus);
        document.setOperatorId(operatorId);
        document.setOccurredAt(now);
        document.setPostedAt(postedAt);
        document.setIdempotencyKey(idem);
        document.setVersion(0);
        document.setRemark(trimToNull(remark));
        if (received) {
            document.setReceivedBy(operatorId);
            document.setReceivedAt(now);
        }
        try {
            documentMapper.insert(document);
        } catch (DuplicateKeyException e) {
            throw conflict("移动单据编号或幂等键冲突，请重试");
        }
        return document;
    }

    private MaterialDocumentLine insertLine(Long documentId, Long sourceLineId, Long materialId, Long assetId,
                                            BigDecimal quantity, String positionText) {
        MaterialDocumentLine line = new MaterialDocumentLine();
        line.setId(IdWorker.getId());
        line.setMaterialDocumentId(documentId);
        line.setSourceMaterialDocumentLineId(sourceLineId);
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
        DocumentEvent event = baseEvent(document.getBizDocumentId(), "REVIEW_TASK_READY", null,
                "PENDING_REVIEW", operatorId, now, null, "REVIEW_TASK_READY:" + task.getId());
        event.setTaskId(persisted.getId());
        flowService.appendEvent(event);
    }

    private void appendEvent(Long sharedDocumentId, String type, String fromStatus, String toStatus,
                             Long actorId, Instant now, String reason, String requestKey, Long taskId) {
        DocumentEvent event = baseEvent(sharedDocumentId, type, fromStatus, toStatus, actorId, now, reason, requestKey);
        event.setTaskId(taskId);
        flowService.appendEvent(event);
    }

    private DocumentEvent baseEvent(Long sharedDocumentId, String type, String fromStatus, String toStatus,
                                    Long actorId, Instant now, String reason, String requestKey) {
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
        return event;
    }

    private MaterialDocument requireTransfer(Long id) {
        MaterialDocument document = documentMapper.selectById(id);
        return requireTransfer(document);
    }

    private MaterialDocument requireTransfer(MaterialDocument document) {
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "移动单不存在");
        }
        if (!TYPE_TRANSFER.equals(document.getDocumentType()) || !SCENE_SEND.equals(document.getBusinessScene())
                || document.getPostedAt() == null || document.getFromLocationId() == null
                || document.getToLocationId() == null) {
            throw conflict("来源单据不是已过账的原移动单");
        }
        return document;
    }

    private MaterialDocument findByIdempotency(Long operatorId, String type, String idem) {
        return documentMapper.selectByOperatorTypeIdempotencyForUpdate(operatorId, type, idem);
    }

    private List<MaterialDocumentLine> sourceLines(Long id, boolean forUpdate) {
        if (forUpdate) {
            return lineMapper.selectByDocumentIdForUpdate(id);
        }
        return lineMapper.selectList(Wrappers.<MaterialDocumentLine>lambdaQuery()
                .eq(MaterialDocumentLine::getMaterialDocumentId, id)
                .orderByAsc(MaterialDocumentLine::getId));
    }

    private Map<Long, TransferLineProcessing> processingByLineId(Long id) {
        return lineMapper.selectTransferProcessingBySourceDocument(id).stream()
                .collect(Collectors.toMap(TransferLineProcessing::sourceLineId, Function.identity()));
    }

    private List<MaterialDocument> childDocuments(Long id) {
        return documentMapper.selectList(Wrappers.<MaterialDocument>lambdaQuery()
                .eq(MaterialDocument::getSourceMaterialDocumentId, id)
                .orderByAsc(MaterialDocument::getOccurredAt)
                .orderByAsc(MaterialDocument::getId));
    }

    private Map<Long, StockLocation> lockLocations(Long firstId, Long secondId) {
        Map<Long, StockLocation> result = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>(Set.of(firstId, secondId));
        ids.sort(Long::compareTo);
        for (Long id : ids) {
            StockLocation location = locationMapper.selectByIdForUpdate(id);
            if (location == null) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在：" + id);
            }
            result.put(id, location);
        }
        return result;
    }

    private void requireEnabled(StockLocation location) {
        if (location == null || !Integer.valueOf(1).equals(location.getEnabled())) {
            throw conflict("目标或来源库位已停用，不能办理该移动操作");
        }
    }

    private Material requireMaterial(Long id, MaterialType expectedType) {
        Material material = materialMapper.selectById(id);
        TrackMode expectedTrack = expectedType == MaterialType.CONSUMABLE ? TrackMode.QUANTITY : TrackMode.SERIAL;
        if (material == null || material.getMaterialType() != expectedType || material.getTrackMode() != expectedTrack) {
            throw badRequest("物料不存在或跟踪类型不符合移动要求：" + id);
        }
        return material;
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

    private Map<Long, FixedAsset> loadAssets(List<MaterialDocumentLine> lines) {
        Set<Long> ids = lines.stream().map(MaterialDocumentLine::getFixedAssetId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : assetMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(FixedAsset::getId, Function.identity()));
    }

    private Map<Long, StockLocation> loadLocations(List<MaterialDocument> documents) {
        Set<Long> ids = new HashSet<>();
        for (MaterialDocument document : documents) {
            if (document.getFromLocationId() != null) ids.add(document.getFromLocationId());
            if (document.getToLocationId() != null) ids.add(document.getToLocationId());
        }
        return ids.isEmpty() ? Map.of() : locationMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StockLocation::getId, Function.identity()));
    }

    private Map<Long, SysUser> loadOperators(List<MaterialDocument> documents) {
        Set<Long> ids = documents.stream().map(MaterialDocument::getOperatorId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private TransferSummaryVO toSummary(MaterialDocument document, Map<Long, StockLocation> locations,
                                        Map<Long, SysUser> users) {
        StockLocation from = locations.get(document.getFromLocationId());
        StockLocation to = locations.get(document.getToLocationId());
        SysUser operator = users.get(document.getOperatorId());
        return new TransferSummaryVO(str(document.getId()), str(document.getBizDocumentId()), document.getDocumentNo(),
                str(document.getFromLocationId()), from == null ? null : from.getLocationName(),
                str(document.getToLocationId()), to == null ? null : to.getLocationName(),
                document.getTransferStatus(), str(document.getOperatorId()),
                operator == null ? null : operator.getRealName(), document.getOccurredAt(), document.getPostedAt(),
                str(document.getReceivedBy()), document.getReceivedAt(), document.getVersion(),
                document.getRemark(), document.getCreateTime());
    }

    private List<ItemDocumentEventVO> sharedEvents(MaterialDocument original, List<MaterialDocument> children) {
        List<MaterialDocument> documents = new ArrayList<>();
        documents.add(original);
        documents.addAll(children);
        List<DocumentEvent> events = documents.stream()
                .flatMap(document -> flowService.listEvents(document.getBizDocumentId()).stream())
                .sorted(Comparator.comparing(DocumentEvent::getOccurredAt)
                        .thenComparing(DocumentEvent::getId))
                .toList();
        Set<Long> actorIds = events.stream().map(DocumentEvent::getActorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> actors = actorIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(actorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return events.stream().map(event -> {
            SysUser actor = event.getActorId() == null ? null : actors.get(event.getActorId());
            String actorName = "SYSTEM".equals(event.getActorType()) ? "系统"
                    : actor == null ? null : actor.getRealName();
            return new ItemDocumentEventVO(str(event.getId()), str(event.getDocumentId()), event.getEventType(),
                    event.getFromStatus(), event.getToStatus(), str(event.getActorId()), actorName,
                    event.getActorType(), event.getOccurredAt(), event.getReason(), event.getRequestKey());
        }).toList();
    }

    private String movementKey(Long documentId, Long lineId, String direction) {
        return documentId + ":" + lineId + ":" + direction;
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
    private String str(Long id) { return id == null ? null : String.valueOf(id); }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ResultCode.CONFLICT.getCode(), message);
    }

    private record CreateLine(Material material, Long assetId, BigDecimal quantity, String positionText) {
    }

    private record ProcessLine(MaterialDocumentLine sourceLine, Material material,
                               BigDecimal quantity, String positionText) {
    }

    private record WriteoffLine(MaterialDocumentLine sourceLine, Material material, BigDecimal quantity,
                                String settlementType, String reason) {
    }
}
