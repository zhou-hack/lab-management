package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.entity.DocumentEvent;
import com.labmanage.module.item.dto.ClaimIssueCreateDTO;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.entity.MaterialClaimLine;
import com.labmanage.module.item.entity.MaterialDocument;
import com.labmanage.module.item.entity.MaterialDocumentLine;
import com.labmanage.module.item.entity.MaterialReservation;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.AssetStatus;
import com.labmanage.module.item.enums.ClaimStatus;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.ReservationStatus;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.MaterialClaimLineMapper;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.MaterialReservationMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.service.ReservationService.IssueDemand;
import com.labmanage.module.item.service.AssistantGrantService.PickupResolution;
import com.labmanage.module.item.vo.ClaimIssueContextVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClaimIssueService {

    private static final String TYPE_OUTBOUND = "OUTBOUND";
    private static final String SCENE_ISSUE = "CLAIM_ISSUE";
    private static final String SHARED_DOCUMENT_TYPE = "ITEM_CLAIM_ISSUE";
    private static final long DEFAULT_DEPARTMENT_ID = 1L;

    private final MaterialClaimMapper claimMapper;
    private final MaterialClaimLineMapper claimLineMapper;
    private final MaterialDocumentMapper documentMapper;
    private final MaterialDocumentLineMapper documentLineMapper;
    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final StockBalanceMapper balanceMapper;
    private final FixedAssetMapper assetMapper;
    private final MaterialReservationMapper reservationMapper;
    private final SysUserMapper userMapper;
    private final ReservationService reservationService;
    private final StockPostingService postingService;
    private final StockDocumentService documentService;
    private final ItemFlowService flowService;
    private final ItemTimeService timeService;
    private final ItemLocationAccessService locationAccessService;
    private final ClaimPlanService planService;
    private final AssistantGrantService assistantGrantService;
    private final MaterialQuantityValidator quantityValidator;

    public ClaimIssueService(MaterialClaimMapper claimMapper,
                             MaterialClaimLineMapper claimLineMapper,
                             MaterialDocumentMapper documentMapper,
                             MaterialDocumentLineMapper documentLineMapper,
                             MaterialMapper materialMapper,
                             StockLocationMapper locationMapper,
                             StockBalanceMapper balanceMapper,
                             FixedAssetMapper assetMapper,
                             MaterialReservationMapper reservationMapper,
                             SysUserMapper userMapper,
                             ReservationService reservationService,
                             StockPostingService postingService,
                             StockDocumentService documentService,
                             ItemFlowService flowService,
                             ItemTimeService timeService,
                             ItemLocationAccessService locationAccessService,
                             ClaimPlanService planService,
                             AssistantGrantService assistantGrantService,
                             MaterialQuantityValidator quantityValidator) {
        this.claimMapper = claimMapper;
        this.claimLineMapper = claimLineMapper;
        this.documentMapper = documentMapper;
        this.documentLineMapper = documentLineMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.balanceMapper = balanceMapper;
        this.assetMapper = assetMapper;
        this.reservationMapper = reservationMapper;
        this.userMapper = userMapper;
        this.reservationService = reservationService;
        this.postingService = postingService;
        this.documentService = documentService;
        this.flowService = flowService;
        this.timeService = timeService;
        this.locationAccessService = locationAccessService;
        this.planService = planService;
        this.assistantGrantService = assistantGrantService;
        this.quantityValidator = quantityValidator;
    }

    public ClaimIssueContextVO issueContext(Long claimId, Long locationId) {
        MaterialClaim claim = requireClaim(claimId);
        StockLocation location = requireLocation(locationId);
        PickupResolution pickup = assistantGrantService.resolvePickup(claim);
        Long operatorId = SecurityUtils.currentUserId();
        List<MaterialClaimLine> currentLines = claimLineMapper.selectList(
                Wrappers.<MaterialClaimLine>lambdaQuery()
                        .eq(MaterialClaimLine::getClaimId, claimId)
                        .eq(MaterialClaimLine::getRevisionNo, claim.getRevisionNo())
                        .orderByAsc(MaterialClaimLine::getId));
        List<MaterialClaimLine> unclaimedLines = currentLines.stream()
                .filter(line -> line.getRequestedQuantity().subtract(line.getIssuedQuantity()).signum() > 0)
                .toList();
        Map<Long, Material> materials = loadMaterialsIncludingDeleted(currentLines);
        List<MaterialReservation> allReservations = reservationService.listCurrent(claimId, claim.getRevisionNo());
        List<MaterialReservation> effectiveReservations = allReservations.stream()
                .filter(reservation -> reservation.getStatus() == ReservationStatus.ACTIVE)
                .filter(reservation -> reservationService.remaining(reservation).signum() > 0)
                .toList();
        Set<Long> assetIds = effectiveReservations.stream().map(MaterialReservation::getFixedAssetId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, FixedAsset> reservedAssets = assetIds.isEmpty() ? Map.of()
                : assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(FixedAsset::getId, Function.identity()));
        Set<Long> locationIds = effectiveReservations.stream().map(MaterialReservation::getLocationId)
                .collect(Collectors.toSet());
        locationIds.add(locationId);
        Map<Long, StockLocation> locations = locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(StockLocation::getId, Function.identity()));

        String basicBlockedReason = basicBlockedReason(claim, operatorId, location, unclaimedLines);
        List<ClaimIssueContextVO.Line> lines = new ArrayList<>();
        boolean hasIssuableLine = false;
        for (MaterialClaimLine line : unclaimedLines) {
            Material material = materials.get(line.getMaterialId());
            List<MaterialReservation> lineAllReservations = allReservations.stream()
                    .filter(reservation -> line.getId().equals(reservation.getClaimLineId())).toList();
            List<MaterialReservation> lineEffectiveReservations = effectiveReservations.stream()
                    .filter(reservation -> line.getId().equals(reservation.getClaimLineId())).toList();
            List<ClaimIssueContextVO.Position> positions = List.of();
            List<ClaimIssueContextVO.Asset> candidates = List.of();
            boolean physicalAvailable = false;
            if (material.getMaterialType() == MaterialType.CONSUMABLE) {
                positions = balanceMapper.selectByMaterialId(material.getId(), false).stream()
                        .filter(balance -> locationId.equals(balance.getLocationId()))
                        .map(balance -> new ClaimIssueContextVO.Position(balance.getPositionText(),
                                balance.getOwnedQuantity(), balance.getAvailableQuantity()))
                        .toList();
                physicalAvailable = positions.stream()
                        .anyMatch(position -> position.availableQuantity().signum() > 0);
            } else {
                ItemTimeService.ReservationWindow window = reservationWindow(claim, lineAllReservations);
                Set<Long> blockedAssetIds = reservationMapper.selectEffectiveOverlapping(material.getId(),
                                window.startAt(), window.endAt(), claimId).stream()
                        .map(MaterialReservation::getFixedAssetId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
                Set<Long> ownAssetIds = lineEffectiveReservations.stream()
                        .map(MaterialReservation::getFixedAssetId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
                candidates = assetMapper.selectByMaterialId(material.getId(), false).stream()
                        .filter(asset -> locationId.equals(asset.getLocationId()))
                        .filter(asset -> asset.getStatus() == AssetStatus.IDLE)
                        .filter(asset -> !blockedAssetIds.contains(asset.getId()))
                        .map(asset -> toAssetVO(asset, ownAssetIds.contains(asset.getId())))
                        .toList();
                physicalAvailable = !candidates.isEmpty();
            }
            List<ClaimIssueContextVO.Reservation> reservationVOs = lineEffectiveReservations.stream()
                    .map(reservation -> toReservationVO(reservation, locations, reservedAssets))
                    .toList();
            if (physicalAvailable && !lineEffectiveReservations.isEmpty()) {
                hasIssuableLine = true;
            }
            lines.add(new ClaimIssueContextVO.Line(str(line.getId()), str(material.getId()),
                    material.getMaterialName(), material.getSpecification(), material.getMaterialType(),
                    material.getQuantityMode(), material.getBaseUnit(), line.getRequestedQuantity(),
                    line.getIssuedQuantity(), line.getRequestedQuantity().subtract(line.getIssuedQuantity()),
                    positions, candidates, reservationVOs));
        }
        String blockedReason = basicBlockedReason;
        if (blockedReason == null && !pickup.valid()) {
            blockedReason = pickup.blockedReason();
        }
        if (blockedReason == null && !hasIssuableLine) {
            blockedReason = "所选库位没有同时具备有效预占和实物可用的未领取行";
        }
        return new ClaimIssueContextVO(str(claimId), claim.getRevisionNo(), str(locationId),
                location.getLocationCode(), location.getLocationName(), claim.getBookingStartAt(),
                claim.getBookingEndAt(), timeService.issueEarliestAt(claim.getBookingDate()),
                blockedReason == null, pickup.pickupMode(), str(pickup.pickupUserId()), pickup.pickupUserName(),
                blockedReason, planService.riskWarnings(), lines);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StockDocumentDetailVO issue(Long claimId, ClaimIssueCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        Long locationId = Ids.parse(dto.locationId());
        MaterialClaim claim = requireClaimForUpdate(claimId);
        PickupResolution pickup = assistantGrantService.lockAndResolvePickup(claim);
        if (!pickup.valid()) {
            throw conflict(pickup.blockedReason());
        }
        StockLocation location = requireLocation(locationId);
        locationAccessService.requireLocationAdmin(operatorId, locationId);
        String idem = documentService.normalizeIdempotencyKey(dto.idempotencyKey());
        MaterialDocument existing = documentMapper.selectByOperatorTypeIdempotencyForUpdate(
                operatorId, TYPE_OUTBOUND, idem);
        if (existing != null) {
            if (!claimId.equals(existing.getBusinessChainId()) || !SCENE_ISSUE.equals(existing.getBusinessScene())
                    || !locationId.equals(existing.getLocationId())) {
                throw conflict("该幂等键已用于其他出库单");
            }
            return documentService.detail(existing.getId());
        }

        ClaimStatus previousStatus = claim.getStatus();
        if (previousStatus != ClaimStatus.APPROVED && previousStatus != ClaimStatus.PARTIALLY_ISSUED) {
            throw conflict("已关闭、过期或非有效审核状态的申领不得实发");
        }
        if (!Integer.valueOf(1).equals(location.getEnabled())) {
            throw conflict("实际库位已停用，不能实发");
        }
        Instant now = timeService.now();
        if (!timeService.isIssueWindow(now, claim.getBookingDate())) {
            throw conflict("当前时间不在实发窗口内");
        }

        List<MaterialClaimLine> currentLines = claimLineMapper.selectCurrentByClaimIdForUpdate(
                claimId, claim.getRevisionNo());
        if (currentLines.isEmpty()) {
            throw conflict("申领当前版本没有物料行");
        }
        Map<Long, Material> materials = loadMaterials(currentLines);
        List<ResolvedGroup> groups = resolveLines(dto.lines(), currentLines, materials);
        List<MaterialReservation> lockedReservations = reservationService.lockForIssue(claimId);
        Map<Long, FixedAsset> lockedAssets = lockAndValidatePhysicalStock(groups, locationId);

        Instant postedAt = timeService.now();
        if (!timeService.isIssueWindow(postedAt, claim.getBookingDate())) {
            throw conflict("等待库存锁期间已超出实发窗口");
        }
        List<IssueDemand> demands = groups.stream()
                .map(group -> new IssueDemand(group.claimLine.getId(), group.material.getMaterialType(),
                        group.quantity, group.material.getMaterialType() == MaterialType.FIXED_ASSET
                        ? group.lines.stream().map(ResolvedLine::fixedAssetId).toList() : List.of()))
                .toList();
        reservationService.consumeForIssue(claim, locationId, demands, lockedReservations, operatorId);

        MaterialDocument document = createDocument(claim, locationId, operatorId, pickup.pickupUserId(),
                idem, dto.remark(), postedAt);
        for (ResolvedGroup group : groups) {
            for (ResolvedLine resolved : group.lines) {
                MaterialDocumentLine documentLine = insertDocumentLine(document.getId(), resolved, lockedAssets);
                if (group.material.getMaterialType() == MaterialType.CONSUMABLE) {
                    postingService.postConsumable(new StockPostingService.ConsumablePosting(
                            resolved.material().getId(), locationId, resolved.positionText(), resolved.quantity(),
                            BigDecimal.ZERO, resolved.quantity().negate(), SCENE_ISSUE, "OUT",
                            document.getId(), documentLine.getId(), operatorId, postedAt,
                            movementKey(document.getId(), documentLine.getId(), "OUT")));
                } else {
                    FixedAsset asset = lockedAssets.get(resolved.fixedAssetId());
                    postingService.transitionAsset(new StockPostingService.AssetTransitionPosting(
                            asset.getId(), AssetStatus.IDLE, locationId, AssetStatus.ON_LOAN, locationId,
                            asset.getPositionText(), locationId, SCENE_ISSUE, "OUT", document.getId(),
                            documentLine.getId(), operatorId, postedAt,
                            movementKey(document.getId(), documentLine.getId(), "OUT")));
                }
            }
        }

        Map<Long, BigDecimal> issuedDeltas = groups.stream().collect(Collectors.toMap(
                group -> group.claimLine.getId(), group -> group.quantity));
        for (ResolvedGroup group : groups) {
            if (claimLineMapper.increaseIssuedQuantity(group.claimLine.getId(), claimId,
                    claim.getRevisionNo(), group.quantity, postedAt, operatorId) != 1) {
                throw conflict("申领行累计实发数量已超过申请数量，请刷新后重试");
            }
        }
        boolean fulfilled = currentLines.stream().allMatch(line -> {
            BigDecimal issued = line.getIssuedQuantity().add(issuedDeltas.getOrDefault(line.getId(), BigDecimal.ZERO));
            return issued.compareTo(line.getRequestedQuantity()) == 0;
        });
        ClaimStatus nextStatus = fulfilled ? ClaimStatus.CLOSED : ClaimStatus.PARTIALLY_ISSUED;
        if (fulfilled) {
            reservationService.releaseRemaining(claimId, claim.getRevisionNo(), operatorId);
        }
        int updated = claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, claimId)
                .eq(MaterialClaim::getRevisionNo, claim.getRevisionNo())
                .eq(MaterialClaim::getStatus, previousStatus)
                .set(MaterialClaim::getStatus, nextStatus)
                .set(fulfilled, MaterialClaim::getCloseReason, "FULFILLED")
                .set(fulfilled, MaterialClaim::getClosedBy, operatorId)
                .set(fulfilled, MaterialClaim::getClosedAt, postedAt)
                .set(MaterialClaim::getUpdateTime, postedAt)
                .set(MaterialClaim::getUpdateUserId, operatorId));
        if (updated != 1) {
            throw conflict("申领状态已变化，请刷新后重试");
        }
        appendClaimEvent(claim, previousStatus, nextStatus, document.getId(), operatorId, dto.remark(), postedAt);
        appendStockEvent(document, operatorId, postedAt, dto.remark());
        return documentService.detail(document.getId());
    }

    private List<ResolvedGroup> resolveLines(List<ClaimIssueCreateDTO.Line> inputLines,
                                             List<MaterialClaimLine> currentLines,
                                             Map<Long, Material> materials) {
        Map<Long, MaterialClaimLine> linesById = currentLines.stream()
                .collect(Collectors.toMap(MaterialClaimLine::getId, Function.identity()));
        Map<Long, ResolvedGroup> groups = new LinkedHashMap<>();
        Set<Long> assetIds = new HashSet<>();
        for (ClaimIssueCreateDTO.Line input : inputLines) {
            quantityValidator.requirePositiveInteger(input.quantity(), "实发数量");
            Long claimLineId = Ids.parse(input.claimLineId());
            MaterialClaimLine claimLine = linesById.get(claimLineId);
            if (claimLine == null) {
                throw badRequest("实发出库行必须属于本申领当前版本：" + claimLineId);
            }
            Material material = materials.get(claimLine.getMaterialId());
            if (material == null) {
                throw conflict("申领行物料不存在");
            }
            ResolvedGroup group = groups.computeIfAbsent(claimLineId,
                    ignored -> new ResolvedGroup(claimLine, material));
            if (material.getMaterialType() == MaterialType.CONSUMABLE) {
                if (group.quantity.signum() > 0) {
                    throw badRequest("同一耗材申领行在一次实发中只能出现一次");
                }
                if (input.fixedAssetId() != null && !input.fixedAssetId().isBlank()) {
                    throw badRequest("耗材实发行不能指定固定资产");
                }
                group.lines.add(new ResolvedLine(claimLine, material, null,
                        input.quantity(), normalizePosition(input.positionText())));
                group.quantity = input.quantity();
            } else {
                if (input.fixedAssetId() == null || input.fixedAssetId().isBlank()) {
                    throw badRequest("固定资产实发必须逐台指定fixedAssetId");
                }
                if (input.quantity().compareTo(BigDecimal.ONE) != 0) {
                    throw badRequest("固定资产每次每行实发数量必须为1");
                }
                Long assetId = Ids.parse(input.fixedAssetId());
                if (!assetIds.add(assetId)) {
                    throw badRequest("同一台固定资产不能在一次实发中重复出现");
                }
                group.lines.add(new ResolvedLine(claimLine, material, assetId, BigDecimal.ONE, null));
                group.quantity = group.quantity.add(BigDecimal.ONE);
            }
            BigDecimal issuedAfter = claimLine.getIssuedQuantity().add(group.quantity);
            if (issuedAfter.compareTo(claimLine.getRequestedQuantity()) > 0) {
                throw conflict("申领行累计实发数量不能超过申请数量：" + claimLineId);
            }
        }
        List<ResolvedGroup> result = new ArrayList<>(groups.values());
        result.sort(Comparator.comparing(group -> group.material.getId()));
        return result;
    }

    private Map<Long, FixedAsset> lockAndValidatePhysicalStock(List<ResolvedGroup> groups, Long locationId) {
        Map<Long, FixedAsset> assets = new HashMap<>();
        for (ResolvedGroup group : groups) {
            if (group.material.getMaterialType() == MaterialType.CONSUMABLE) {
                ResolvedLine line = group.lines.get(0);
                StockBalance balance = balanceMapper.selectForUpdate(group.material.getId(), locationId,
                        line.positionText());
                if (balance == null || balance.getAvailableQuantity().compareTo(group.quantity) < 0) {
                    throw conflict("实际库位该position耗材可用量不足：" + group.material.getMaterialName());
                }
                continue;
            }
            List<ResolvedLine> ordered = group.lines.stream()
                    .sorted(Comparator.comparing(ResolvedLine::fixedAssetId)).toList();
            for (ResolvedLine line : ordered) {
                FixedAsset asset = assetMapper.selectByIdForUpdate(line.fixedAssetId());
                if (asset == null || !group.material.getId().equals(asset.getMaterialId())) {
                    throw badRequest("固定资产不存在或不属于申领行物料：" + line.fixedAssetId());
                }
                if (asset.getStatus() != AssetStatus.IDLE || !locationId.equals(asset.getLocationId())) {
                    throw conflict("固定资产必须当前IDLE且位于实际库位：" + asset.getAssetCode());
                }
                assets.put(asset.getId(), asset);
            }
        }
        return assets;
    }

    private MaterialDocument createDocument(MaterialClaim claim, Long locationId, Long operatorId, Long pickupUserId,
                                            String idem, String remark, Instant now) {
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
        document.setDocumentType(TYPE_OUTBOUND);
        document.setBusinessScene(SCENE_ISSUE);
        document.setDocumentSeq(documentMapper.selectNextDocumentSeq(claim.getId(), TYPE_OUTBOUND));
        document.setLocationId(locationId);
        document.setOperatorId(operatorId);
        document.setResponsibleTeacherId(claim.getApplicantId());
        document.setPickupUserId(pickupUserId);
        document.setOccurredAt(now);
        document.setPostedAt(now);
        document.setProcessStatus("UNPROCESSED");
        document.setIdempotencyKey(idem);
        document.setVersion(0);
        document.setRemark(trimToNull(remark));
        try {
            documentMapper.insert(document);
        } catch (DuplicateKeyException e) {
            throw conflict("出库单编号、序号或幂等键冲突，请重试");
        }
        return document;
    }

    private MaterialDocumentLine insertDocumentLine(Long documentId, ResolvedLine resolved,
                                                     Map<Long, FixedAsset> lockedAssets) {
        MaterialDocumentLine line = new MaterialDocumentLine();
        line.setId(IdWorker.getId());
        line.setMaterialDocumentId(documentId);
        line.setClaimLineId(resolved.claimLine().getId());
        line.setMaterialId(resolved.material().getId());
        line.setFixedAssetId(resolved.fixedAssetId());
        line.setQuantity(resolved.quantity());
        line.setPositionText(resolved.fixedAssetId() == null ? resolved.positionText()
                : lockedAssets.get(resolved.fixedAssetId()).getPositionText());
        documentLineMapper.insert(line);
        return line;
    }

    private void appendClaimEvent(MaterialClaim claim, ClaimStatus from, ClaimStatus to, Long stockDocumentId,
                                  Long operatorId, String remark, Instant now) {
        DocumentEvent event = baseEvent(claim.getBizDocumentId(), "ISSUE", operatorId, now, remark);
        event.setFromStatus(from.name());
        event.setToStatus(to.name());
        event.setRequestKey("ITEM_CLAIM:" + claim.getId() + ":ISSUE:" + stockDocumentId);
        flowService.appendEvent(event);
    }

    private void appendStockEvent(MaterialDocument document, Long operatorId, Instant now, String remark) {
        DocumentEvent event = baseEvent(document.getBizDocumentId(), "STOCK_OUT", operatorId, now, remark);
        event.setToStatus("POSTED");
        event.setRequestKey("STOCK_OUT:" + document.getId());
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

    private String basicBlockedReason(MaterialClaim claim, Long operatorId, StockLocation location,
                                      List<MaterialClaimLine> unclaimedLines) {
        if (claim.getStatus() != ClaimStatus.APPROVED && claim.getStatus() != ClaimStatus.PARTIALLY_ISSUED) {
            return "当前申领状态不可实发或已关闭、过期";
        }
        if (!timeService.isIssueWindow(timeService.now(), claim.getBookingDate())) {
            return "当前时间不在实发窗口内";
        }
        if (!Integer.valueOf(1).equals(location.getEnabled())) {
            return "实际库位已停用";
        }
        if (!locationAccessService.isLocationAdmin(operatorId, location.getId())) {
            return "当前用户不是实际库位管理员";
        }
        if (unclaimedLines.isEmpty()) {
            return "当前版本没有未领取行";
        }
        return null;
    }

    private ItemTimeService.ReservationWindow reservationWindow(MaterialClaim claim,
                                                                 List<MaterialReservation> reservations) {
        if (!reservations.isEmpty()) {
            MaterialReservation reservation = reservations.get(0);
            return new ItemTimeService.ReservationWindow(reservation.getReservationStartAt(),
                    reservation.getReservationEndAt());
        }
        return timeService.normalReservationWindow(claim.getBookingStartAt(), claim.getBookingEndAt());
    }

    private ClaimIssueContextVO.Asset toAssetVO(FixedAsset asset, boolean reservedByThisClaim) {
        return new ClaimIssueContextVO.Asset(str(asset.getId()), asset.getAssetCode(), str(asset.getLocationId()),
                asset.getPositionText(), asset.getStatus(), reservedByThisClaim);
    }

    private ClaimIssueContextVO.Reservation toReservationVO(MaterialReservation reservation,
                                                             Map<Long, StockLocation> locations,
                                                             Map<Long, FixedAsset> assets) {
        StockLocation location = locations.get(reservation.getLocationId());
        FixedAsset asset = reservation.getFixedAssetId() == null ? null : assets.get(reservation.getFixedAssetId());
        return new ClaimIssueContextVO.Reservation(str(reservation.getId()), str(reservation.getAllocationId()),
                str(reservation.getLocationId()), location == null ? null : location.getLocationCode(),
                location == null ? null : location.getLocationName(), str(reservation.getFixedAssetId()),
                asset == null ? null : asset.getAssetCode(), reservation.getReservationRule(),
                reservation.getReservedQuantity(), reservation.getConsumedQuantity(), reservation.getReleasedQuantity(),
                reservationService.remaining(reservation), reservation.getReservationStartAt(),
                reservation.getReservationEndAt());
    }

    private Map<Long, Material> loadMaterials(List<MaterialClaimLine> lines) {
        Set<Long> ids = lines.stream().map(MaterialClaimLine::getMaterialId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : materialMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private Map<Long, Material> loadMaterialsIncludingDeleted(List<MaterialClaimLine> lines) {
        Set<Long> ids = lines.stream().map(MaterialClaimLine::getMaterialId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : materialMapper.selectBatchIdsIncludingDeleted(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private MaterialClaim requireClaim(Long id) {
        MaterialClaim claim = claimMapper.selectById(id);
        if (claim == null) {
            throw notFound("申领单不存在");
        }
        return claim;
    }

    private MaterialClaim requireClaimForUpdate(Long id) {
        MaterialClaim claim = claimMapper.selectByIdForUpdate(id);
        if (claim == null) {
            throw notFound("申领单不存在");
        }
        return claim;
    }

    private StockLocation requireLocation(Long id) {
        StockLocation location = locationMapper.selectById(id);
        if (location == null) {
            throw notFound("库位不存在");
        }
        return location;
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

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private static final class ResolvedGroup {
        private final MaterialClaimLine claimLine;
        private final Material material;
        private final List<ResolvedLine> lines = new ArrayList<>();
        private BigDecimal quantity = BigDecimal.ZERO;

        private ResolvedGroup(MaterialClaimLine claimLine, Material material) {
            this.claimLine = claimLine;
            this.material = material;
        }
    }

    private record ResolvedLine(
            MaterialClaimLine claimLine,
            Material material,
            Long fixedAssetId,
            BigDecimal quantity,
            String positionText
    ) {
    }
}
