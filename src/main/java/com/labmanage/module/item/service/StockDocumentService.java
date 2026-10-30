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
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.item.dto.ItemPageQuery;
import com.labmanage.module.item.dto.StockDocumentPageQuery;
import com.labmanage.module.item.dto.StockInboundCreateDTO;
import com.labmanage.module.item.dto.StockInboundLineDTO;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialDocument;
import com.labmanage.module.item.entity.MaterialDocumentLine;
import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.StockMovement;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.TrackMode;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.vo.ItemDocumentEventVO;
import com.labmanage.module.item.vo.StockDocumentDetailVO;
import com.labmanage.module.item.vo.StockDocumentLineVO;
import com.labmanage.module.item.vo.StockDocumentVO;
import com.labmanage.module.item.vo.StockSettlementVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.module.item.validation.MaterialQuantityValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockDocumentService {

    public static final String SCENE_OPENING = "OPENING";
    public static final String SCENE_REPLENISHMENT = "REPLENISHMENT";
    public static final String TYPE_INBOUND = "INBOUND";

    private static final long DEFAULT_DEPARTMENT_ID = 1L;
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "documentNo", "document_no",
            "documentType", "document_type",
            "occurredAt", "occurred_at",
            "createTime", "create_time"
    );

    private final MaterialDocumentMapper documentMapper;
    private final MaterialDocumentLineMapper lineMapper;
    private final MaterialSettlementMapper settlementMapper;
    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final FixedAssetMapper assetMapper;
    private final SysUserMapper userMapper;
    private final ItemFlowService flowService;
    private final ItemTimeService timeService;
    private final StockPostingService postingService;
    private final ItemLocationAccessService locationAccessService;
    private final MaterialQuantityValidator quantityValidator;

    public StockDocumentService(MaterialDocumentMapper documentMapper,
                                MaterialDocumentLineMapper lineMapper,
                                MaterialSettlementMapper settlementMapper,
                                MaterialMapper materialMapper,
                                StockLocationMapper locationMapper,
                                FixedAssetMapper assetMapper,
                                SysUserMapper userMapper,
                                ItemFlowService flowService,
                                ItemTimeService timeService,
                                StockPostingService postingService,
                                ItemLocationAccessService locationAccessService,
                                MaterialQuantityValidator quantityValidator) {
        this.documentMapper = documentMapper;
        this.lineMapper = lineMapper;
        this.settlementMapper = settlementMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.assetMapper = assetMapper;
        this.userMapper = userMapper;
        this.flowService = flowService;
        this.timeService = timeService;
        this.postingService = postingService;
        this.locationAccessService = locationAccessService;
        this.quantityValidator = quantityValidator;
    }

    @Transactional(rollbackFor = Exception.class)
    public StockDocumentDetailVO createOpening(StockInboundCreateDTO dto) {
        return createConsumableInbound(SCENE_OPENING, dto);
    }

    @Transactional(rollbackFor = Exception.class)
    public StockDocumentDetailVO createReplenishment(StockInboundCreateDTO dto) {
        return createConsumableInbound(SCENE_REPLENISHMENT, dto);
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialDocument createAssetInbound(FixedAsset asset, String scene,
                                               String idempotencyKey, String remark) {
        String normalizedScene = normalizeBusinessScene(scene);
        String documentType = documentTypeOf(normalizedScene);
        Long operatorId = SecurityUtils.currentUserId();
        String idem = normalizeIdempotencyKey(idempotencyKey);
        requireIdempotencyAvailable(normalizedScene, idem);
        StockLocation location = requireEnabledLocation(asset.getLocationId());
        locationAccessService.requireLocationAdmin(operatorId, location.getId());

        Instant now = timeService.now();
        MaterialDocument document = createDocumentShell(normalizedScene, documentType,
                location.getId(), operatorId, idem, remark, now);
        MaterialDocumentLine line = insertLine(document.getId(), asset.getMaterialId(), asset.getId(),
                BigDecimal.ONE, normalizePosition(asset.getPositionText()));
        postingService.recordAssetMovement(new StockPostingService.AssetMovementPosting(
                asset.getId(), asset.getMaterialId(), location.getId(), normalizedScene, "IN",
                document.getId(), line.getId(), operatorId, now,
                movementKey(document.getId(), line.getId())));
        appendPostedEvent(document, operatorId, now, remark);
        return document;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialDocument createImportBatchInbound(List<FixedAsset> assets, List<ImportConsumableLine> consumables,
                                                     Long locationId, String scene,
                                                     String idempotencyKey, String remark) {
        boolean hasAssets = assets != null && !assets.isEmpty();
        boolean hasConsumables = consumables != null && !consumables.isEmpty();
        if (!hasAssets && !hasConsumables) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "批量入库明细不能为空");
        }
        String normalizedScene = normalizeBusinessScene(scene);
        String documentType = documentTypeOf(normalizedScene);
        Long operatorId = SecurityUtils.currentUserId();
        String idem = normalizeIdempotencyKey(idempotencyKey);
        requireIdempotencyAvailable(normalizedScene, idem);
        requireEnabledLocation(locationId);
        locationAccessService.requireLocationAdmin(operatorId, locationId);

        Instant now = timeService.now();
        MaterialDocument document = createDocumentShell(normalizedScene, documentType,
                locationId, operatorId, idem, remark, now);
        if (hasAssets) {
            for (FixedAsset asset : assets) {
                if (!locationId.equals(asset.getLocationId())) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "批量入库资产必须属于同一库位");
                }
                MaterialDocumentLine line = insertLine(document.getId(), asset.getMaterialId(), asset.getId(),
                        BigDecimal.ONE, normalizePosition(asset.getPositionText()));
                postingService.recordAssetMovement(new StockPostingService.AssetMovementPosting(
                        asset.getId(), asset.getMaterialId(), locationId, normalizedScene, "IN",
                        document.getId(), line.getId(), operatorId, now,
                        movementKey(document.getId(), line.getId())));
            }
        }
        List<StockPostingService.ConsumablePosting> postings = new ArrayList<>();
        if (hasConsumables) {
            for (ImportConsumableLine consumable : consumables) {
                Material material = materialMapper.selectById(consumable.materialId());
                if (material == null || material.getMaterialType() != MaterialType.CONSUMABLE
                        || material.getTrackMode() != TrackMode.QUANTITY) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "导入行物料不是数量跟踪耗材");
                }
                BigDecimal quantity = consumable.quantity();
                quantityValidator.requirePositiveInteger(quantity, "耗材入库数量");
                MaterialDocumentLine line = insertLine(document.getId(), consumable.materialId(), null,
                        quantity, null);
                postings.add(new StockPostingService.ConsumablePosting(
                        consumable.materialId(), locationId, null, quantity, quantity, quantity,
                        normalizedScene, "IN", document.getId(), line.getId(), operatorId, now,
                        movementKey(document.getId(), line.getId())));
            }
        }
        postings.sort(Comparator.comparing(StockPostingService.ConsumablePosting::materialId)
                .thenComparing(StockPostingService.ConsumablePosting::locationId)
                .thenComparing(p -> p.positionText() == null ? "" : p.positionText())
                .thenComparing(StockPostingService.ConsumablePosting::materialDocumentLineId));
        postings.forEach(postingService::postConsumable);
        appendPostedEvent(document, operatorId, now, remark);
        return document;
    }

    public MaterialDocument findInboundByIdempotency(String scene, String idempotencyKey) {
        return findByIdempotency(SecurityUtils.currentUserId(), documentTypeOf(scene),
                normalizeIdempotencyKey(idempotencyKey));
    }

    public record ImportConsumableLine(Long materialId, BigDecimal quantity) {
    }

    public String normalizeBusinessScene(String requestedType) {
        String value = requestedType == null || requestedType.isBlank()
                ? SCENE_OPENING : requestedType.trim();
        if (!SCENE_OPENING.equals(value) && !SCENE_REPLENISHMENT.equals(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "首次入库类型仅支持OPENING/REPLENISHMENT");
        }
        return value;
    }

    public String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String value = idempotencyKey.trim();
        if (value.length() > 128) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "幂等键长度不能超过128");
        }
        return value;
    }

    public void requireIdempotencyAvailable(String scene, String idempotencyKey) {
        Long operatorId = SecurityUtils.currentUserId();
        MaterialDocument existing = findByIdempotency(operatorId, documentTypeOf(scene), idempotencyKey);
        if (existing != null) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该幂等键已用于其他库存单据");
        }
    }

    public PageResult<StockDocumentVO> page(StockDocumentPageQuery query) {
        validateDateRange(query.getStartDate(), query.getEndDate());
        Instant startAt = query.getStartDate() == null ? null : dayStart(query.getStartDate());
        Instant endAt = query.getEndDate() == null ? null : dayStart(query.getEndDate().plusDays(1));
        LambdaQueryWrapper<MaterialDocument> wrapper = Wrappers.<MaterialDocument>lambdaQuery()
                .like(hasText(query.getDocumentNo()), MaterialDocument::getDocumentNo, trim(query.getDocumentNo()))
                .eq(hasText(query.getDocumentType()), MaterialDocument::getDocumentType, trim(query.getDocumentType()))
                .eq(hasText(query.getBusinessScene()), MaterialDocument::getBusinessScene, trim(query.getBusinessScene()))
                .eq(query.getBusinessChainId() != null, MaterialDocument::getBusinessChainId, query.getBusinessChainId())
                .eq(query.getOperatorId() != null, MaterialDocument::getOperatorId, query.getOperatorId())
                .ge(startAt != null, MaterialDocument::getOccurredAt, startAt)
                .lt(endAt != null, MaterialDocument::getOccurredAt, endAt);
        if (query.getLocationId() != null) {
            wrapper.and(w -> w.eq(MaterialDocument::getLocationId, query.getLocationId())
                    .or().eq(MaterialDocument::getFromLocationId, query.getLocationId())
                    .or().eq(MaterialDocument::getToLocationId, query.getLocationId())
                    .or().exists("SELECT 1 FROM stock_movement sm "
                            + "WHERE sm.material_document_id = material_document.id "
                            + "AND sm.location_id = {0} AND sm.deleted = 0", query.getLocationId()));
        }
        applySort(wrapper, query, SORT_COLUMNS, MaterialDocument::getOccurredAt);
        Page<MaterialDocument> page = documentMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<MaterialDocument> records = page.getRecords();
        Map<Long, StockLocation> locations = loadDocumentLocations(records);
        Map<Long, SysUser> users = loadDocumentUsers(records);
        List<StockDocumentVO> vos = records.stream().map(d -> toVO(d, locations, users)).toList();
        return PageResult.of(vos, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    public StockDocumentDetailVO detail(Long id) {
        MaterialDocument document = requireDocument(id);
        Map<Long, StockLocation> locations = loadDocumentLocations(List.of(document));
        Map<Long, SysUser> users = new HashMap<>(loadDocumentUsers(List.of(document)));
        List<MaterialDocumentLine> lines = lineMapper.selectList(Wrappers.<MaterialDocumentLine>lambdaQuery()
                .eq(MaterialDocumentLine::getMaterialDocumentId, id)
                .orderByAsc(MaterialDocumentLine::getId));
        List<MaterialSettlement> settlements = settlementMapper.selectList(Wrappers.<MaterialSettlement>lambdaQuery()
                .eq(MaterialSettlement::getMaterialDocumentId, id)
                .orderByAsc(MaterialSettlement::getId));
        Map<Long, Material> materials = new HashMap<>(loadMaterials(lines));
        Map<Long, FixedAsset> assets = new HashMap<>(loadAssets(lines));
        Set<Long> settlementMaterialIds = settlements.stream().map(MaterialSettlement::getMaterialId)
                .collect(Collectors.toSet());
        settlementMaterialIds.removeAll(materials.keySet());
        if (!settlementMaterialIds.isEmpty()) {
            materials.putAll(materialMapper.selectBatchIdsIncludingDeleted(settlementMaterialIds).stream()
                    .collect(Collectors.toMap(Material::getId, Function.identity())));
        }
        Set<Long> settlementAssetIds = settlements.stream().map(MaterialSettlement::getFixedAssetId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        settlementAssetIds.removeAll(assets.keySet());
        if (!settlementAssetIds.isEmpty()) {
            assets.putAll(assetMapper.selectBatchIds(settlementAssetIds).stream()
                    .collect(Collectors.toMap(FixedAsset::getId, Function.identity())));
        }
        List<DocumentEvent> events = flowService.listEvents(document.getBizDocumentId());
        Set<Long> actorIds = events.stream().map(DocumentEvent::getActorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        actorIds.removeAll(users.keySet());
        if (!actorIds.isEmpty()) {
            users.putAll(userMapper.selectBatchIds(actorIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, Function.identity())));
        }
        List<StockDocumentLineVO> lineVOs = lines.stream()
                .map(line -> toLineVO(line, materials, assets)).toList();
        List<StockSettlementVO> settlementVOs = settlements.stream()
                .map(settlement -> toSettlementVO(settlement, materials, assets)).toList();
        List<ItemDocumentEventVO> eventVOs = events.stream().map(event -> toEventVO(event, users)).toList();
        return new StockDocumentDetailVO(toVO(document, locations, users), lineVOs, settlementVOs, eventVOs);
    }

    public List<StockDocumentDetailVO> listClaimDocuments(Long businessChainId) {
        List<MaterialDocument> documents = documentMapper.selectList(Wrappers.<MaterialDocument>lambdaQuery()
                .eq(MaterialDocument::getBusinessChainId, businessChainId)
                .and(wrapper -> wrapper
                        .eq(MaterialDocument::getDocumentType, "OUTBOUND")
                        .eq(MaterialDocument::getBusinessScene, "CLAIM_ISSUE")
                        .or()
                        .eq(MaterialDocument::getDocumentType, "INBOUND")
                        .eq(MaterialDocument::getBusinessScene, "CLAIM_RETURN"))
                .orderByAsc(MaterialDocument::getOccurredAt)
                .orderByAsc(MaterialDocument::getId));
        return documents.stream().map(document -> detail(document.getId())).toList();
    }

    private StockDocumentDetailVO createConsumableInbound(String scene, StockInboundCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        String documentType = documentTypeOf(scene);
        String idem = normalizeIdempotencyKey(dto.idempotencyKey());
        MaterialDocument existing = findByIdempotency(operatorId, documentType, idem);
        if (existing != null) {
            return detail(existing.getId());
        }

        List<ResolvedInboundLine> resolved = new ArrayList<>(dto.lines().size());
        Set<Long> locationIds = new HashSet<>();
        for (StockInboundLineDTO line : dto.lines()) {
            Long materialId = Ids.parse(line.materialId());
            Long locationId = Ids.parse(line.locationId());
            Material material = materialMapper.selectById(materialId);
            if (material == null || material.getMaterialType() != MaterialType.CONSUMABLE
                    || material.getTrackMode() != TrackMode.QUANTITY) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "入库行物料不存在或不是数量跟踪耗材：" + materialId);
            }
            quantityValidator.requirePositiveInteger(line.quantity(), "入库数量");
            StockLocation location = requireEnabledLocation(locationId);
            locationAccessService.requireLocationAdmin(operatorId, locationId);
            locationIds.add(locationId);
            resolved.add(new ResolvedInboundLine(materialId, locationId,
                    normalizePosition(line.positionText()), line.quantity()));
        }

        Instant now = timeService.now();
        Long documentLocationId = locationIds.size() == 1 ? locationIds.iterator().next() : null;
        MaterialDocument document = createDocumentShell(scene, documentType, documentLocationId,
                operatorId, idem, trimToNull(dto.remark()), now);
        List<StockPostingService.ConsumablePosting> postings = new ArrayList<>(resolved.size());
        for (ResolvedInboundLine line : resolved) {
            MaterialDocumentLine documentLine = insertLine(document.getId(), line.materialId(), null,
                    line.quantity(), line.positionText());
            postings.add(new StockPostingService.ConsumablePosting(
                    line.materialId(), line.locationId(), line.positionText(), line.quantity(),
                    line.quantity(), line.quantity(), scene, "IN", document.getId(), documentLine.getId(),
                    operatorId, now, movementKey(document.getId(), documentLine.getId())));
        }
        postings.sort(Comparator.comparing(StockPostingService.ConsumablePosting::materialId)
                .thenComparing(StockPostingService.ConsumablePosting::locationId)
                .thenComparing(p -> p.positionText() == null ? "" : p.positionText())
                .thenComparing(StockPostingService.ConsumablePosting::materialDocumentLineId));
        postings.forEach(postingService::postConsumable);
        appendPostedEvent(document, operatorId, now, dto.remark());
        return detail(document.getId());
    }

    private MaterialDocument createDocumentShell(String scene, String documentType, Long locationId,
                                                  Long operatorId, String idem, String remark, Instant now) {
        Long documentId = IdWorker.getId();
        String documentNo = "IM" + documentId;
        SysUser operator = userMapper.selectById(operatorId);
        try {
            BizDocument shared = new BizDocument();
            shared.setId(IdWorker.getId());
            shared.setDocumentNo(documentNo);
            shared.setDocumentType("ITEM_" + scene);
            shared.setOwnerUserId(operatorId);
            shared.setDepartmentId(operator == null || operator.getDepartmentId() == null
                    ? DEFAULT_DEPARTMENT_ID : operator.getDepartmentId());
            shared.setSubmittedAt(now);
            shared.setIdempotencyKey(idem);
            BizDocument persistedShared = flowService.createDocument(shared);

            MaterialDocument document = new MaterialDocument();
            document.setId(documentId);
            document.setBizDocumentId(persistedShared.getId());
            document.setDocumentNo(documentNo);
            document.setDocumentType(documentType);
            document.setBusinessScene(scene);
            document.setLocationId(locationId);
            document.setOperatorId(operatorId);
            document.setOccurredAt(now);
            document.setPostedAt(now);
            document.setIdempotencyKey(idem);
            document.setVersion(0);
            document.setRemark(trimToNull(remark));
            documentMapper.insert(document);
            return document;
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库存单据编号或幂等键冲突，请重试");
        }
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

    private void appendPostedEvent(MaterialDocument document, Long operatorId, Instant now, String remark) {
        DocumentEvent event = new DocumentEvent();
        event.setId(IdWorker.getId());
        event.setDocumentId(document.getBizDocumentId());
        event.setEventType("STOCK_IN");
        event.setToStatus("POSTED");
        event.setActorId(operatorId);
        event.setActorType("USER");
        event.setOccurredAt(now);
        event.setReason(trimToNull(remark));
        event.setRequestKey("STOCK_IN:" + document.getId());
        flowService.appendEvent(event);
    }

    private StockLocation requireEnabledLocation(Long locationId) {
        StockLocation location = locationMapper.selectById(locationId);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在");
        }
        if (!Integer.valueOf(1).equals(location.getEnabled())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位已停用，不能办理入库");
        }
        return location;
    }

    private MaterialDocument findByIdempotency(Long operatorId, String documentType, String idem) {
        return documentMapper.selectOne(Wrappers.<MaterialDocument>lambdaQuery()
                .eq(MaterialDocument::getOperatorId, operatorId)
                .eq(MaterialDocument::getDocumentType, documentType)
                .eq(MaterialDocument::getIdempotencyKey, idem)
                .last("LIMIT 1"));
    }

    private MaterialDocument requireDocument(Long id) {
        MaterialDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库存单据不存在");
        }
        return document;
    }

    private String documentTypeOf(String scene) {
        return SCENE_OPENING.equals(scene) ? SCENE_OPENING : TYPE_INBOUND;
    }

    private String movementKey(Long documentId, Long lineId) {
        return documentId + ":" + lineId + ":IN";
    }

    private Map<Long, StockLocation> loadDocumentLocations(List<MaterialDocument> documents) {
        Set<Long> ids = new HashSet<>();
        for (MaterialDocument document : documents) {
            if (document.getLocationId() != null) ids.add(document.getLocationId());
            if (document.getFromLocationId() != null) ids.add(document.getFromLocationId());
            if (document.getToLocationId() != null) ids.add(document.getToLocationId());
        }
        return ids.isEmpty() ? Map.of() : locationMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StockLocation::getId, Function.identity()));
    }

    private Map<Long, SysUser> loadDocumentUsers(List<MaterialDocument> documents) {
        Set<Long> ids = new HashSet<>();
        for (MaterialDocument document : documents) {
            if (document.getOperatorId() != null) ids.add(document.getOperatorId());
            if (document.getPickupUserId() != null) ids.add(document.getPickupUserId());
        }
        return ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private Map<Long, Material> loadMaterials(List<MaterialDocumentLine> lines) {
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

    private StockDocumentVO toVO(MaterialDocument d, Map<Long, StockLocation> locations,
                                 Map<Long, SysUser> users) {
        StockLocation location = d.getLocationId() == null ? null : locations.get(d.getLocationId());
        SysUser operator = users.get(d.getOperatorId());
        SysUser pickupUser = users.get(d.getPickupUserId());
        return new StockDocumentVO(str(d.getId()), str(d.getBizDocumentId()), d.getDocumentNo(),
                str(d.getBusinessChainId()), d.getDocumentType(), d.getBusinessScene(), d.getDocumentSeq(),
                str(d.getSourceMaterialDocumentId()), str(d.getLocationId()),
                location == null ? null : location.getLocationName(), str(d.getFromLocationId()),
                str(d.getToLocationId()), d.getTransferStatus(), str(d.getReceivedBy()), d.getReceivedAt(),
                d.getReviewStatus(), str(d.getReviewedBy()), d.getReviewedAt(), d.getReviewReason(),
                str(d.getOperatorId()), operator == null ? null : operator.getRealName(),
                str(d.getResponsibleTeacherId()), str(d.getPickupUserId()),
                pickupUser == null ? null : pickupUser.getRealName(), d.getOccurredAt(), d.getPostedAt(), d.getProcessStatus(),
                d.getReturnStatus(), d.getOverdueReason(), d.getVersion(), d.getRemark(), d.getCreateTime());
    }

    private StockDocumentLineVO toLineVO(MaterialDocumentLine line, Map<Long, Material> materials,
                                         Map<Long, FixedAsset> assets) {
        Material material = materials.get(line.getMaterialId());
        FixedAsset asset = line.getFixedAssetId() == null ? null : assets.get(line.getFixedAssetId());
        return new StockDocumentLineVO(str(line.getId()), str(line.getMaterialDocumentId()),
                str(line.getClaimLineId()), str(line.getSourceMaterialDocumentLineId()), str(line.getMaterialId()),
                material == null ? null : material.getMaterialName(),
                material == null ? null : material.getSpecification(),
                material == null ? null : material.getBaseUnit(), str(line.getFixedAssetId()),
                asset == null ? null : asset.getAssetCode(), line.getQuantity(), line.getPositionText());
    }

    private StockSettlementVO toSettlementVO(MaterialSettlement settlement, Map<Long, Material> materials,
                                             Map<Long, FixedAsset> assets) {
        Material material = materials.get(settlement.getMaterialId());
        FixedAsset asset = settlement.getFixedAssetId() == null ? null : assets.get(settlement.getFixedAssetId());
        return new StockSettlementVO(str(settlement.getId()), str(settlement.getMaterialDocumentId()),
                str(settlement.getSourceMaterialDocumentLineId()), str(settlement.getMaterialId()),
                material == null ? null : material.getMaterialName(), str(settlement.getFixedAssetId()),
                asset == null ? null : asset.getAssetCode(), settlement.getSettlementType(),
                settlement.getQuantity(), settlement.getTargetAssetStatus(), settlement.getReason());
    }

    private ItemDocumentEventVO toEventVO(DocumentEvent event, Map<Long, SysUser> users) {
        SysUser actor = event.getActorId() == null ? null : users.get(event.getActorId());
        String actorName = "SYSTEM".equals(event.getActorType()) ? "系统"
                : actor == null ? null : actor.getRealName();
        return new ItemDocumentEventVO(str(event.getId()), str(event.getDocumentId()), event.getEventType(),
                event.getFromStatus(), event.getToStatus(), str(event.getActorId()), actorName,
                event.getActorType(), event.getOccurredAt(), event.getReason(), event.getRequestKey());
    }

    private <T> void applySort(LambdaQueryWrapper<T> wrapper, ItemPageQuery query,
                               Map<String, String> whitelist,
                               com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> defaultColumn) {
        String column = query.getSortBy() == null ? null : whitelist.get(query.getSortBy());
        if (column == null) {
            wrapper.orderByDesc(defaultColumn);
            return;
        }
        wrapper.last("ORDER BY " + column + ("asc".equalsIgnoreCase(query.getSortOrder()) ? " ASC" : " DESC")
                + (column.equals("occurred_at") ? "" : ", occurred_at DESC"));
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "开始日期不能晚于结束日期");
        }
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(ItemTimeService.SHANGHAI).toInstant();
    }

    private String normalizePosition(String value) {
        String position = trimToNull(value);
        if (position != null && position.length() > 256) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "库内位置长度不能超过256");
        }
        return position;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String trimToNull(String value) { return hasText(value) ? value.trim() : null; }
    private String str(Long id) { return id == null ? null : String.valueOf(id); }

    private record ResolvedInboundLine(Long materialId, Long locationId,
                                       String positionText, BigDecimal quantity) {
    }
}
