package com.labmanage.module.item.service;

import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialReservation;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.enums.AssetStatus;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper.OutstandingLoan;
import com.labmanage.module.item.mapper.MaterialReservationMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StockAvailabilityService {

    public static final String BOOKING_WINDOW = "BOOKING_WINDOW";
    public static final String SHANGHAI_DAY_LOW_TOTAL = "SHANGHAI_DAY_LOW_TOTAL";

    private final StockBalanceMapper balanceMapper;
    private final FixedAssetMapper assetMapper;
    private final MaterialReservationMapper reservationMapper;
    private final MaterialDocumentLineMapper documentLineMapper;
    private final ItemTimeService timeService;

    public StockAvailabilityService(StockBalanceMapper balanceMapper,
                                    FixedAssetMapper assetMapper,
                                    MaterialReservationMapper reservationMapper,
                                    MaterialDocumentLineMapper documentLineMapper,
                                    ItemTimeService timeService) {
        this.balanceMapper = balanceMapper;
        this.assetMapper = assetMapper;
        this.reservationMapper = reservationMapper;
        this.documentLineMapper = documentLineMapper;
        this.timeService = timeService;
    }

    /**
     * 加载同一物料的库存快照。审核传入 lockResources=true 时，按库位/余额ID或资产ID
     * 固定顺序锁定该物料全部库存资源，再复用可用量计算；本方法不写预占。
     */
    public StockSnapshot loadSnapshot(Material material,
                                      ItemTimeService.BookingWindow bookingWindow,
                                      Instant asOf,
                                      Long excludeClaimId,
                                      boolean lockResources) {
        if (material.getMaterialType() == MaterialType.FIXED_ASSET) {
            List<FixedAsset> assets = assetMapper.selectByMaterialId(material.getId(), lockResources);
            int ownedAssetTotal = (int) assets.stream()
                    .filter(asset -> asset.getStatus() != AssetStatus.LOST
                            && asset.getStatus() != AssetStatus.SCRAPPED)
                    .count();
            ItemTimeService.ReservationWindow window = timeService.reservationWindow(bookingWindow, ownedAssetTotal);
            String rule = ownedAssetTotal < 5 ? SHANGHAI_DAY_LOW_TOTAL : BOOKING_WINDOW;
            return new StockSnapshot(material, bookingWindow, asOf, rule, window, ownedAssetTotal,
                    List.of(), assets, overlappingReservations(material.getId(), window, excludeClaimId),
                    documentLineMapper.selectOutstandingByMaterial(material.getId()));
        }

        ItemTimeService.ReservationWindow window = timeService.normalReservationWindow(
                bookingWindow.bookingStartAt(), bookingWindow.bookingEndAt());
        List<StockBalance> balances = balanceMapper.selectByMaterialId(material.getId(), lockResources);
        return new StockSnapshot(material, bookingWindow, asOf, BOOKING_WINDOW, window, null,
                balances, List.of(), overlappingReservations(material.getId(), window, excludeClaimId),
                documentLineMapper.selectOutstandingByMaterial(material.getId()));
    }

    /** 计算单库可约量；耗材按数量，固定资产按逐台候选结果。 */
    public LocationAvailability availability(StockSnapshot snapshot, Long locationId) {
        if (snapshot.material().getMaterialType() == MaterialType.FIXED_ASSET) {
            return fixedAssetAvailability(snapshot, locationId);
        }
        BigDecimal current = snapshot.balances().stream()
                .filter(balance -> Objects.equals(balance.getLocationId(), locationId))
                .map(StockBalance::getAvailableQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedReturns = snapshot.outstandingLoans().stream()
                .filter(loan -> Objects.equals(loan.locationId(), locationId))
                .filter(loan -> isExpectedReturn(loan, snapshot))
                .map(OutstandingLoan::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserved = snapshot.reservations().stream()
                .filter(reservation -> Objects.equals(reservation.getLocationId(), locationId))
                .map(this::remainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = current.add(expectedReturns).subtract(reserved).max(BigDecimal.ZERO);
        return new LocationAvailability(current, expectedReturns, reserved, available, List.of());
    }

    public BigDecimal availableQuantity(StockSnapshot snapshot, Long locationId) {
        return availability(snapshot, locationId).availableQuantity();
    }

    public List<FixedAsset> candidateAssets(StockSnapshot snapshot, Long locationId) {
        return availability(snapshot, locationId).candidateAssets();
    }

    private LocationAvailability fixedAssetAvailability(StockSnapshot snapshot, Long locationId) {
        Set<Long> reservedAssetIds = snapshot.reservations().stream()
                .map(MaterialReservation::getFixedAssetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<FixedAsset> viableAssets = snapshot.assets().stream()
                .filter(asset -> Objects.equals(asset.getLocationId(), locationId))
                .filter(asset -> asset.getStatus() == AssetStatus.IDLE
                        || (asset.getStatus() == AssetStatus.ON_LOAN && isExpectedReturn(asset, snapshot)))
                .toList();
        long idle = viableAssets.stream().filter(asset -> asset.getStatus() == AssetStatus.IDLE).count();
        long expectedReturns = viableAssets.size() - idle;
        long reserved = viableAssets.stream().filter(asset -> reservedAssetIds.contains(asset.getId())).count();
        List<FixedAsset> candidates = viableAssets.stream()
                .filter(asset -> !reservedAssetIds.contains(asset.getId()))
                .toList();
        return new LocationAvailability(BigDecimal.valueOf(idle), BigDecimal.valueOf(expectedReturns),
                BigDecimal.valueOf(reserved), BigDecimal.valueOf(candidates.size()), candidates);
    }

    private boolean isExpectedReturn(FixedAsset asset, StockSnapshot snapshot) {
        List<OutstandingLoan> loans = snapshot.outstandingLoans().stream()
                .filter(loan -> Objects.equals(loan.fixedAssetId(), asset.getId()))
                .toList();
        return !loans.isEmpty() && loans.stream().allMatch(loan -> isExpectedReturn(loan, snapshot));
    }

    private boolean isExpectedReturn(OutstandingLoan loan, StockSnapshot snapshot) {
        return loan.returnDeadline() != null
                && !loan.returnDeadline().isBefore(snapshot.asOf())
                && !loan.returnDeadline().isAfter(snapshot.bookingWindow().bookingStartAt());
    }

    private BigDecimal remainingQuantity(MaterialReservation reservation) {
        return reservation.getReservedQuantity()
                .subtract(reservation.getConsumedQuantity())
                .subtract(reservation.getReleasedQuantity());
    }

    private List<MaterialReservation> overlappingReservations(Long materialId,
                                                               ItemTimeService.ReservationWindow window,
                                                               Long excludeClaimId) {
        return reservationMapper.selectEffectiveOverlapping(materialId, window.startAt(), window.endAt(), excludeClaimId);
    }

    public record StockSnapshot(
            Material material,
            ItemTimeService.BookingWindow bookingWindow,
            Instant asOf,
            String reservationRule,
            ItemTimeService.ReservationWindow reservationWindow,
            Integer ownedAssetTotal,
            List<StockBalance> balances,
            List<FixedAsset> assets,
            List<MaterialReservation> reservations,
            List<OutstandingLoan> outstandingLoans
    ) {
    }

    public record LocationAvailability(
            BigDecimal currentQuantity,
            BigDecimal expectedReturnQuantity,
            BigDecimal overlappingReservationQuantity,
            BigDecimal availableQuantity,
            List<FixedAsset> candidateAssets
    ) {
    }
}
