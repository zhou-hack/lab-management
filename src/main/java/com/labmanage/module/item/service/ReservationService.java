package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.entity.MaterialClaimAllocation;
import com.labmanage.module.item.entity.MaterialClaimLine;
import com.labmanage.module.item.entity.MaterialClaimSegment;
import com.labmanage.module.item.entity.MaterialReservation;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.ReservationStatus;
import com.labmanage.module.item.mapper.MaterialClaimSegmentMapper;
import com.labmanage.module.item.mapper.MaterialReservationMapper;
import com.labmanage.module.item.service.ClaimPlanService.ApprovalPreview;
import com.labmanage.module.item.vo.ClaimPlannedAllocationVO;
import com.labmanage.module.item.vo.BookingTimeSegmentVO;
import com.labmanage.module.item.vo.ClaimPreviewLineVO;
import com.labmanage.module.item.validation.MaterialQuantityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReservationService {

    private final MaterialReservationMapper reservationMapper;
    private final MaterialClaimSegmentMapper segmentMapper;
    private final ItemTimeService timeService;
    private final MaterialQuantityValidator quantityValidator;

    public ReservationService(MaterialReservationMapper reservationMapper,
                              MaterialClaimSegmentMapper segmentMapper,
                              ItemTimeService timeService,
                              MaterialQuantityValidator quantityValidator) {
        this.reservationMapper = reservationMapper;
        this.segmentMapper = segmentMapper;
        this.timeService = timeService;
        this.quantityValidator = quantityValidator;
    }

    /**
     * 按审核锁内快照创建预占。调用方必须已锁申领主行，并通过 ClaimPlanService.previewForApproval
     * 锁定计划物料及全部相关库存资源。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<MaterialReservation> createForClaim(MaterialClaim claim,
                                                     List<MaterialClaimLine> lines,
                                                     List<MaterialClaimAllocation> allocations,
                                                     ApprovalPreview approvalPreview) {
        Map<Long, MaterialClaimLine> linesByMaterial = new HashMap<>();
        for (MaterialClaimLine line : lines) {
            linesByMaterial.put(line.getMaterialId(), line);
        }
        Map<String, MaterialClaimAllocation> allocationMap = new HashMap<>();
        for (MaterialClaimAllocation allocation : allocations) {
            allocationMap.put(allocationKey(allocation.getClaimLineId(), allocation.getLocationId()), allocation);
        }
        Map<Long, ClaimPreviewLineVO> previewLines = new HashMap<>();
        for (ClaimPreviewLineVO line : approvalPreview.preview().lines()) {
            previewLines.put(Ids.parse(line.materialId()), line);
        }
        Map<Integer, MaterialClaimSegment> segmentsBySeq = currentSegmentsBySeq(claim);

        List<MaterialReservation> result = new ArrayList<>();
        Set<String> usedAssetSegments = new HashSet<>();
        for (ClaimPlannedAllocationVO planned : approvalPreview.preview().allocations()) {
            Long materialId = Ids.parse(planned.materialId());
            Long locationId = Ids.parse(planned.locationId());
            MaterialClaimLine line = linesByMaterial.get(materialId);
            ClaimPreviewLineVO previewLine = previewLines.get(materialId);
            MaterialClaimAllocation allocation = line == null ? null
                    : allocationMap.get(allocationKey(line.getId(), locationId));
            if (line == null || previewLine == null || allocation == null
                    || allocation.getPlannedQuantity().compareTo(planned.plannedQuantity()) != 0) {
                throw conflict("审核计划与当前申领分配不一致");
            }
            List<Long> sharedAssetIds = null;
            for (BookingTimeSegmentVO segment : approvalPreview.preview().bookingSnapshot().segments()) {
                MaterialClaimSegment claimSegment = segmentsBySeq.get(segment.segmentSeq());
                if (claimSegment == null) {
                    throw conflict("申领节次段与当前版本不一致");
                }
                ItemTimeService.ReservationWindow window = reservationWindow(claim, segment, previewLine);
                if (previewLine.materialType() == MaterialType.FIXED_ASSET) {
                    int quantity = exactAssetQuantity(planned.plannedQuantity());
                    List<Long> candidates = approvalPreview.candidateAssets(materialId, locationId, segment.segmentSeq());
                    if (sharedAssetIds == null) {
                        if (candidates.size() < quantity) {
                            throw conflict("固定资产候选数量已变化，请重新审核");
                        }
                        sharedAssetIds = List.copyOf(candidates.subList(0, quantity));
                    } else if (!candidates.containsAll(sharedAssetIds)) {
                        throw conflict("固定资产须在各预约段均可使用，请重新审核");
                    }
                    for (Long assetId : sharedAssetIds) {
                        if (!usedAssetSegments.add(segment.segmentSeq() + ":" + assetId)) {
                            throw conflict("同一台固定资产不能在同一预约段重复预占");
                        }
                        result.add(insertReservation(claim, line, allocation, claimSegment, previewLine,
                                assetId, BigDecimal.ONE, window));
                    }
                } else {
                    result.add(insertReservation(claim, line, allocation, claimSegment, previewLine,
                            null, planned.plannedQuantity(), window));
                }
            }
        }
        return result;
    }

    private Map<Integer, MaterialClaimSegment> currentSegmentsBySeq(MaterialClaim claim) {
        List<MaterialClaimSegment> segments = segmentMapper.selectList(Wrappers.<MaterialClaimSegment>lambdaQuery()
                .eq(MaterialClaimSegment::getClaimId, claim.getId())
                .eq(MaterialClaimSegment::getRevisionNo, claim.getRevisionNo())
                .orderByAsc(MaterialClaimSegment::getSegmentSeq));
        if (segments.isEmpty()) {
            MaterialClaimSegment legacy = new MaterialClaimSegment();
            legacy.setClaimId(claim.getId());
            legacy.setRevisionNo(claim.getRevisionNo());
            legacy.setSegmentSeq(1);
            legacy.setStartPeriodId(claim.getStartPeriodId());
            legacy.setEndPeriodId(claim.getEndPeriodId());
            legacy.setBookingStartAt(claim.getBookingStartAt());
            legacy.setBookingEndAt(claim.getBookingEndAt());
            return Map.of(1, legacy);
        }
        return segments.stream().collect(java.util.stream.Collectors.toMap(
                MaterialClaimSegment::getSegmentSeq, java.util.function.Function.identity()));
    }

    private ItemTimeService.ReservationWindow reservationWindow(MaterialClaim claim,
                                                                BookingTimeSegmentVO segment,
                                                                ClaimPreviewLineVO previewLine) {
        if (previewLine.materialType() == MaterialType.CONSUMABLE) {
            return timeService.normalReservationWindow(segment.bookingStartAt(), segment.bookingEndAt());
        }
        ItemTimeService.BookingWindow bookingWindow = new ItemTimeService.BookingWindow(
                claim.getBookingDate(), segment.bookingStartAt(), segment.bookingEndAt(),
                claim.getReturnDeadline(), timeService.issueEarliestAt(claim.getBookingDate()));
        return timeService.reservationWindow(bookingWindow, previewLine.ownedAssetTotal());
    }

    /** 释放本申领所有版本的未消耗预占，供修改、取消使用。调用方须先锁申领主行。 */
    @Transactional(rollbackFor = Exception.class)
    public int releaseForClaim(Long claimId, Long operatorId) {
        return release(reservationMapper.selectActiveByClaimIdForUpdate(claimId), null, operatorId);
    }

    /** 释放指定版本未消耗预占，供关闭、过期和后续实发流程复用。调用方须先锁申领主行。 */
    @Transactional(rollbackFor = Exception.class)
    public int releaseRemaining(Long claimId, Integer revisionNo, Long operatorId) {
        return release(reservationMapper.selectActiveByClaimIdForUpdate(claimId), revisionNo, operatorId);
    }

    /** 实发预占锁。调用方须先锁申领主行和当前版本行，并在锁余额、资产前调用。 */
    public List<MaterialReservation> lockForIssue(Long claimId) {
        return reservationMapper.selectActiveByClaimIdForUpdate(claimId);
    }

    /**
     * 实发消耗预占。调用方须先锁申领主行、当前版本行及预占；固定资产实物须已锁定并复验合格。
     * 实际库位匹配部分记 consumed，改库或替换资产部分释放本行其他计划预占。
     */
    @Transactional(rollbackFor = Exception.class)
    public void consumeForIssue(MaterialClaim claim,
                                Long actualLocationId,
                                List<IssueDemand> demands,
                                List<MaterialReservation> locked,
                                Long operatorId) {
        Instant now = timeService.now();
        BigDecimal totalReduced = BigDecimal.ZERO;
        for (IssueDemand demand : demands) {
            quantityValidator.requirePositiveInteger(demand.quantity(), "实发预占消耗数量");
            List<MaterialReservation> lineReservations = locked.stream()
                    .filter(reservation -> claim.getRevisionNo().equals(reservation.getRevisionNo()))
                    .filter(reservation -> demand.claimLineId().equals(reservation.getClaimLineId()))
                    .filter(reservation -> remaining(reservation).signum() > 0)
                    .toList();
            BigDecimal available = lineReservations.stream()
                    .map(this::remaining)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (demand.quantity() == null || demand.quantity().signum() <= 0
                    || available.compareTo(demand.quantity()) < 0) {
                throw conflict("申领行有效预占不足，不能实发");
            }

            BigDecimal reduced;
            if (demand.materialType() == MaterialType.FIXED_ASSET) {
                reduced = consumeFixedAssets(claim, actualLocationId, demand, lineReservations, now, operatorId);
            } else if (demand.materialType() == MaterialType.CONSUMABLE) {
                reduced = consumeConsumable(actualLocationId, demand, lineReservations, now, operatorId);
            } else {
                throw conflict("实发物料类型不合法");
            }
            if (reduced.compareTo(demand.quantity()) != 0) {
                throw conflict("实发数量与预占消耗数量不一致");
            }
            totalReduced = totalReduced.add(reduced);
        }
        BigDecimal demanded = demands.stream().map(IssueDemand::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalReduced.compareTo(demanded) != 0) {
            throw conflict("本次实发与总预占减少量不一致");
        }
    }

    private BigDecimal consumeConsumable(Long actualLocationId,
                                         IssueDemand demand,
                                         List<MaterialReservation> lineReservations,
                                         Instant now,
                                         Long operatorId) {
        BigDecimal remainingToConsume = demand.quantity();
        BigDecimal reduced = BigDecimal.ZERO;
        List<MaterialReservation> used = new ArrayList<>();
        for (MaterialReservation reservation : lineReservations) {
            if (!actualLocationId.equals(reservation.getLocationId())
                    || reservation.getFixedAssetId() != null || remainingToConsume.signum() <= 0) {
                continue;
            }
            BigDecimal quantity = remaining(reservation).min(remainingToConsume);
            consume(reservation, quantity, now, operatorId);
            used.add(reservation);
            remainingToConsume = remainingToConsume.subtract(quantity);
            reduced = reduced.add(quantity);
        }
        BigDecimal remainingToRelease = remainingToConsume;
        for (MaterialReservation reservation : lineReservations) {
            if (remainingToRelease.signum() <= 0) {
                break;
            }
            BigDecimal quantity = remaining(reservation).min(remainingToRelease);
            if (quantity.signum() <= 0) {
                continue;
            }
            release(reservation, quantity, now, operatorId);
            used.add(reservation);
            remainingToRelease = remainingToRelease.subtract(quantity);
            reduced = reduced.add(quantity);
        }
        if (remainingToRelease.signum() > 0) {
            throw conflict("实际库位预占消耗及其他计划预占释放不足");
        }
        releaseOtherSegments(lineReservations, used, now, operatorId);
        return reduced;
    }

    private BigDecimal consumeFixedAssets(MaterialClaim claim,
                                          Long actualLocationId,
                                          IssueDemand demand,
                                          List<MaterialReservation> lineReservations,
                                          Instant now,
                                          Long operatorId) {
        int quantity = exactAssetQuantity(demand.quantity());
        if (demand.fixedAssetIds() == null || demand.fixedAssetIds().size() != quantity
                || new HashSet<>(demand.fixedAssetIds()).size() != quantity) {
            throw conflict("固定资产必须逐台实发且数量合计等于1的整数倍");
        }
        Set<Long> directlyConsumed = new HashSet<>();
        BigDecimal reduced = BigDecimal.ZERO;
        for (Long assetId : demand.fixedAssetIds()) {
            MaterialReservation matched = lineReservations.stream()
                    .filter(reservation -> actualLocationId.equals(reservation.getLocationId()))
                    .filter(reservation -> assetId.equals(reservation.getFixedAssetId()))
                    .filter(reservation -> remaining(reservation).signum() > 0)
                    .findFirst().orElse(null);
            if (matched != null) {
                consume(matched, BigDecimal.ONE, now, operatorId);
                releaseOtherSegments(lineReservations, List.of(matched), now, operatorId);
                directlyConsumed.add(assetId);
                reduced = reduced.add(BigDecimal.ONE);
            }
        }
        for (Long assetId : demand.fixedAssetIds()) {
            if (directlyConsumed.contains(assetId)) {
                continue;
            }
            for (MaterialReservation window : lineReservations) {
                if (!reservationMapper.selectEffectiveOverlappingByAsset(assetId,
                        window.getReservationStartAt(), window.getReservationEndAt(), claim.getId()).isEmpty()) {
                    throw conflict("替代固定资产存在他人重叠有效预占：" + assetId);
                }
            }
            MaterialReservation released = null;
            for (MaterialReservation candidate : lineReservations) {
                if (remaining(candidate).signum() > 0) {
                    released = candidate;
                    break;
                }
            }
            if (released == null) {
                throw conflict("替代固定资产缺少可释放的原计划预占");
            }
            release(released, BigDecimal.ONE, now, operatorId);
            releaseOtherSegments(lineReservations, List.of(released), now, operatorId);
            reduced = reduced.add(BigDecimal.ONE);
        }
        return reduced;
    }

    private void releaseOtherSegments(List<MaterialReservation> lineReservations,
                                      List<MaterialReservation> used,
                                      Instant now, Long operatorId) {
        for (MaterialReservation usedReservation : used) {
            for (MaterialReservation candidate : lineReservations) {
                if (candidate.getId().equals(usedReservation.getId())
                        || !candidate.getAllocationId().equals(usedReservation.getAllocationId())
                        || !java.util.Objects.equals(candidate.getFixedAssetId(), usedReservation.getFixedAssetId())
                        || remaining(candidate).signum() <= 0) {
                    continue;
                }
                release(candidate, remaining(candidate), now, operatorId);
            }
        }
    }

    private void consume(MaterialReservation reservation, BigDecimal quantity,
                         Instant now, Long operatorId) {
        quantityValidator.requirePositiveInteger(quantity, "预占消耗数量");
        if (reservationMapper.consume(
                reservation.getId(), quantity, now, operatorId) != 1) {
            throw conflict("预占消耗失败，请刷新后重试");
        }
        reservation.setConsumedQuantity(reservation.getConsumedQuantity().add(quantity));
        closeIfExhausted(reservation, now);
    }

    private void release(MaterialReservation reservation, BigDecimal quantity,
                         Instant now, Long operatorId) {
        quantityValidator.requirePositiveInteger(quantity, "预占释放数量");
        if (reservationMapper.releaseQuantity(
                reservation.getId(), quantity, now, operatorId) != 1) {
            throw conflict("预占释放失败，请刷新后重试");
        }
        reservation.setReleasedQuantity(reservation.getReleasedQuantity().add(quantity));
        closeIfExhausted(reservation, now);
    }

    private void closeIfExhausted(MaterialReservation reservation, Instant now) {
        if (remaining(reservation).signum() == 0) {
            reservation.setStatus(ReservationStatus.CLOSED);
            reservation.setClosedAt(now);
        }
    }

    /** 当前版本仍 ACTIVE 且有剩余量的预占。 */
    public List<MaterialReservation> listCurrentEffective(Long claimId, Integer revisionNo) {
        return reservationMapper.selectList(Wrappers.<MaterialReservation>lambdaQuery()
                .eq(MaterialReservation::getClaimId, claimId)
                .eq(MaterialReservation::getRevisionNo, revisionNo)
                .eq(MaterialReservation::getStatus, ReservationStatus.ACTIVE)
                .apply("reserved_quantity > consumed_quantity + released_quantity")
                .orderByAsc(MaterialReservation::getId));
    }

    /** 当前版本全部预占（含已关闭），历史版本不混入详情。 */
    public List<MaterialReservation> listCurrent(Long claimId, Integer revisionNo) {
        return reservationMapper.selectList(Wrappers.<MaterialReservation>lambdaQuery()
                .eq(MaterialReservation::getClaimId, claimId)
                .eq(MaterialReservation::getRevisionNo, revisionNo)
                .orderByAsc(MaterialReservation::getId));
    }

    public BigDecimal remaining(MaterialReservation reservation) {
        return reservation.getReservedQuantity()
                .subtract(reservation.getConsumedQuantity())
                .subtract(reservation.getReleasedQuantity());
    }

    private MaterialReservation insertReservation(MaterialClaim claim,
                                                   MaterialClaimLine line,
                                                   MaterialClaimAllocation allocation,
                                                   MaterialClaimSegment claimSegment,
                                                   ClaimPreviewLineVO previewLine,
                                                   Long fixedAssetId,
                                                   BigDecimal quantity,
                                                   ItemTimeService.ReservationWindow window) {
        quantityValidator.requirePositiveInteger(quantity, "预占创建数量");
        MaterialReservation reservation = new MaterialReservation();
        reservation.setId(IdWorker.getId());
        reservation.setClaimId(claim.getId());
        reservation.setClaimLineId(line.getId());
        reservation.setAllocationId(allocation.getId());
        reservation.setClaimSegmentId(claimSegment.getId());
        reservation.setRevisionNo(claim.getRevisionNo());
        reservation.setMaterialId(line.getMaterialId());
        reservation.setLocationId(allocation.getLocationId());
        reservation.setFixedAssetId(fixedAssetId);
        reservation.setReservationRule(previewLine.reservationRule());
        reservation.setAssetTotalSnapshot(previewLine.ownedAssetTotal());
        reservation.setReservedQuantity(quantity);
        reservation.setConsumedQuantity(BigDecimal.ZERO);
        reservation.setReleasedQuantity(BigDecimal.ZERO);
        reservation.setReservationStartAt(window.startAt());
        reservation.setReservationEndAt(window.endAt());
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservationMapper.insert(reservation);
        return reservation;
    }

    private int release(List<MaterialReservation> reservations, Integer revisionNo, Long operatorId) {
        Instant now = timeService.now();
        int released = 0;
        for (MaterialReservation reservation : reservations) {
            if (revisionNo != null && !revisionNo.equals(reservation.getRevisionNo())) {
                continue;
            }
            if (remaining(reservation).signum() <= 0) {
                continue;
            }
            quantityValidator.requirePositiveInteger(remaining(reservation), "预占释放数量");
            if (reservationMapper.releaseRemaining(reservation.getId(), now, operatorId) != 1) {
                throw conflict("预占已变化，请刷新后重试");
            }
            released++;
        }
        return released;
    }

    private int exactAssetQuantity(BigDecimal quantity) {
        try {
            return quantity.intValueExact();
        } catch (ArithmeticException e) {
            throw conflict("固定资产预占数量必须为正整数");
        }
    }

    private String allocationKey(Long claimLineId, Long locationId) {
        return claimLineId + ":" + locationId;
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ResultCode.CONFLICT.getCode(), message);
    }

    public record IssueDemand(
            Long claimLineId,
            MaterialType materialType,
            BigDecimal quantity,
            List<Long> fixedAssetIds
    ) {
    }
}
