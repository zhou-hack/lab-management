package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.ClaimAvailabilityQuery;
import com.labmanage.module.item.dto.ClaimPreviewAllocationDTO;
import com.labmanage.module.item.dto.ClaimPreviewDTO;
import com.labmanage.module.item.dto.ClaimPreviewLineDTO;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.service.StockAvailabilityService.LocationAvailability;
import com.labmanage.module.item.service.StockAvailabilityService.StockSnapshot;
import com.labmanage.module.item.vo.BookingTimeSnapshotVO;
import com.labmanage.module.item.vo.ClaimAvailabilityVO;
import com.labmanage.module.item.vo.ClaimPlannedAllocationVO;
import com.labmanage.module.item.vo.ClaimPreviewLineVO;
import com.labmanage.module.item.vo.ClaimPreviewVO;
import com.labmanage.module.item.vo.ClaimShortageVO;
import com.labmanage.module.item.validation.MaterialQuantityValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
public class ClaimPlanService {

    private static final List<String> RISK_WARNINGS = List.of(
            "前序申领可在其预约当天23:59前归还，预计归还量不保证会在本次预约开始前实际回库。",
            "预占不等于实发时实物保证，实发时将重新校验实际库存。"
    );

    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final ItemTimeService timeService;
    private final StockAvailabilityService availabilityService;
    private final MaterialQuantityValidator quantityValidator;

    public ClaimPlanService(MaterialMapper materialMapper,
                            StockLocationMapper locationMapper,
                            ItemTimeService timeService,
                            StockAvailabilityService availabilityService,
                            MaterialQuantityValidator quantityValidator) {
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.timeService = timeService;
        this.availabilityService = availabilityService;
        this.quantityValidator = quantityValidator;
    }

    public ClaimPreviewVO preview(ClaimPreviewDTO dto) {
        return preview(dto, null);
    }

    public List<String> riskWarnings() {
        return RISK_WARNINGS;
    }

    public PageResult<ClaimAvailabilityVO> availability(ClaimAvailabilityQuery query, Long excludeClaimId) {
        ItemTimeService.BookingWindow bookingWindow = timeService.resolveBookingWindow(
                query.getBookingDate(), Ids.parse(query.getStartPeriodId()), Ids.parse(query.getEndPeriodId()));
        Instant asOf = timeService.now();
        List<StockLocation> locations = locationMapper.selectList(Wrappers.<StockLocation>lambdaQuery()
                .eq(StockLocation::getEnabled, 1)
                .orderByAsc(StockLocation::getId));
        Page<Material> page = materialMapper.selectPage(PageUtils.toPage(query), Wrappers.<Material>lambdaQuery()
                .eq(Material::getEnabled, 1)
                .eq(query.getMaterialType() != null, Material::getMaterialType, query.getMaterialType())
                .like(hasText(query.getMaterialName()), Material::getMaterialName,
                        hasText(query.getMaterialName()) ? query.getMaterialName().trim() : null)
                .orderByAsc(Material::getMaterialName)
                .orderByAsc(Material::getId));
        List<ClaimAvailabilityVO> records = page.getRecords().stream()
                .map(material -> toAvailabilityVO(material, bookingWindow, asOf, excludeClaimId, locations))
                .toList();
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** 修改场景通过 excludeClaimId 排除本申领旧预占；只预检，不创建申领或写入预占。 */
    public ClaimPreviewVO preview(ClaimPreviewDTO dto, Long excludeClaimId) {
        return compute(dto, excludeClaimId, false).preview();
    }

    /**
     * 审核快照：调用方必须先锁申领主行；此处按 materialId 升序锁物料，再按相同顺序
     * 锁各物料全部余额或资产并重跑预览。候选资产仅用于服务端生成预占，不暴露到接口。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalPreview previewForApproval(ClaimPreviewDTO dto) {
        validateRequest(dto);
        dto.lines().stream()
                .map(line -> Ids.parse(line.materialId()))
                .distinct()
                .sorted()
                .forEach(materialMapper::selectByIdForUpdate);
        return compute(dto, null, true);
    }

    private ApprovalPreview compute(ClaimPreviewDTO dto, Long excludeClaimId, boolean lockResources) {
        validateRequest(dto);
        Long startPeriodId = Ids.parse(dto.startPeriodId());
        Long endPeriodId = Ids.parse(dto.endPeriodId());
        ItemTimeService.BookingWindow bookingWindow = timeService.resolveBookingWindow(
                dto.bookingDate(), startPeriodId, endPeriodId);
        List<StockLocation> locations = locationMapper.selectList(Wrappers.<StockLocation>lambdaQuery()
                .eq(StockLocation::getEnabled, 1)
                .orderByAsc(StockLocation::getId));
        Map<Long, StockLocation> locationMap = locations.stream()
                .collect(Collectors.toMap(StockLocation::getId, Function.identity()));
        List<ResolvedLine> lines = resolveLines(dto.lines(), locations, bookingWindow,
                timeService.now(), excludeClaimId, lockResources);

        List<PlannedAllocation> planned = dto.allocations() == null || dto.allocations().isEmpty()
                ? automaticAllocations(lines, locations)
                : providedAllocations(dto.allocations(), lines, locationMap);
        planned.forEach(allocation -> quantityValidator.requirePositiveInteger(
                allocation.plannedQuantity(), "计划分配数量"));
        List<ClaimShortageVO> shortages = shortages(lines, planned, locations.isEmpty());
        List<ClaimPreviewLineVO> lineVOs = lines.stream().map(this::toLineVO).toList();
        List<ClaimPlannedAllocationVO> allocationVOs = planned.stream().map(this::toAllocationVO).toList();
        BookingTimeSnapshotVO snapshot = new BookingTimeSnapshotVO(dto.bookingDate(),
                String.valueOf(startPeriodId), String.valueOf(endPeriodId), bookingWindow.bookingStartAt(),
                bookingWindow.bookingEndAt(), bookingWindow.returnDeadline(), bookingWindow.issueEarliestAt());
        ClaimPreviewVO preview = new ClaimPreviewVO(shortages.isEmpty(), lineVOs, allocationVOs, shortages,
                RISK_WARNINGS, snapshot);
        Map<String, List<Long>> candidateAssetIds = new LinkedHashMap<>();
        for (PlannedAllocation allocation : planned) {
            candidateAssetIds.put(assetKey(allocation.line().materialId(), allocation.location().getId()),
                    allocation.availability().candidateAssets().stream().map(asset -> asset.getId()).toList());
        }
        return new ApprovalPreview(preview, candidateAssetIds);
    }

    private List<ResolvedLine> resolveLines(List<ClaimPreviewLineDTO> inputLines,
                                            List<StockLocation> locations,
                                            ItemTimeService.BookingWindow bookingWindow,
                                            Instant asOf,
                                            Long excludeClaimId,
                                            boolean lockResources) {
        Set<Long> uniqueIds = new HashSet<>();
        List<Long> materialIds = new ArrayList<>();
        for (ClaimPreviewLineDTO line : inputLines) {
            Long materialId = Ids.parse(line.materialId());
            if (!uniqueIds.add(materialId)) {
                throw badRequest("同一物料在申领行中只能出现一次：" + materialId);
            }
            materialIds.add(materialId);
        }
        Map<Long, Material> materials = materialMapper.selectBatchIds(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        List<ResolvedLine> result = new ArrayList<>(Collections.nCopies(inputLines.size(), null));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < inputLines.size(); i++) {
            order.add(i);
        }
        if (lockResources) {
            order.sort(Comparator.comparing(materialIds::get));
        }
        for (int i : order) {
            ClaimPreviewLineDTO input = inputLines.get(i);
            Long materialId = materialIds.get(i);
            Material material = materials.get(materialId);
            if (material == null || !Integer.valueOf(1).equals(material.getEnabled())) {
                throw badRequest("物料不存在或已停用：" + materialId);
            }
            if (material.getMaterialType() == MaterialType.FIXED_ASSET
                    && !isInteger(input.requestedQuantity())) {
                throw badRequest("固定资产申请数量必须为整数：" + materialId);
            }
            StockSnapshot snapshot = availabilityService.loadSnapshot(material, bookingWindow, asOf,
                    excludeClaimId, lockResources);
            Map<Long, LocationAvailability> availability = new LinkedHashMap<>();
            for (StockLocation location : locations) {
                availability.put(location.getId(), availabilityService.availability(snapshot, location.getId()));
            }
            result.set(i, new ResolvedLine(materialId, input.requestedQuantity(), material, snapshot, availability));
        }
        return result;
    }

    private List<PlannedAllocation> automaticAllocations(List<ResolvedLine> lines,
                                                          List<StockLocation> locations) {
        if (locations.isEmpty()) {
            return List.of();
        }
        StockLocation singleLocation = null;
        for (StockLocation location : locations) {
            boolean fitsAll = lines.stream().allMatch(line -> line.availabilityByLocation()
                    .get(location.getId()).availableQuantity().compareTo(line.requestedQuantity()) >= 0);
            if (fitsAll) {
                singleLocation = location;
                break;
            }
        }
        List<PlannedAllocation> result = new ArrayList<>();
        if (singleLocation != null) {
            for (ResolvedLine line : lines) {
                result.add(new PlannedAllocation(line, singleLocation, line.requestedQuantity(),
                        line.availabilityByLocation().get(singleLocation.getId())));
            }
            return result;
        }
        for (ResolvedLine line : lines) {
            result.addAll(automaticLineAllocations(line, locations));
        }
        return result;
    }

    private List<PlannedAllocation> automaticLineAllocations(ResolvedLine line,
                                                              List<StockLocation> locations) {
        List<StockLocation> ordered = new ArrayList<>(locations);
        ordered.sort((left, right) -> {
            int byAvailability = line.availabilityByLocation().get(right.getId()).availableQuantity()
                    .compareTo(line.availabilityByLocation().get(left.getId()).availableQuantity());
            return byAvailability != 0 ? byAvailability : left.getId().compareTo(right.getId());
        });
        List<PlannedAllocation> result = new ArrayList<>();
        BigDecimal remaining = line.requestedQuantity();
        for (StockLocation location : ordered) {
            if (remaining.signum() <= 0) {
                break;
            }
            LocationAvailability availability = line.availabilityByLocation().get(location.getId());
            BigDecimal quantity = remaining.min(availability.availableQuantity());
            if (quantity.signum() > 0) {
                result.add(new PlannedAllocation(line, location, quantity, availability));
                remaining = remaining.subtract(quantity);
            }
        }
        if (remaining.signum() > 0) {
            StockLocation bestLocation = ordered.get(0);
            int existingIndex = -1;
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).location().getId().equals(bestLocation.getId())) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex >= 0) {
                PlannedAllocation existing = result.get(existingIndex);
                result.set(existingIndex, new PlannedAllocation(line, bestLocation,
                        existing.plannedQuantity().add(remaining), existing.availability()));
            } else {
                result.add(new PlannedAllocation(line, bestLocation, remaining,
                        line.availabilityByLocation().get(bestLocation.getId())));
            }
        }
        return result;
    }

    private List<PlannedAllocation> providedAllocations(List<ClaimPreviewAllocationDTO> input,
                                                         List<ResolvedLine> lines,
                                                         Map<Long, StockLocation> locationMap) {
        Map<Long, ResolvedLine> lineMap = lines.stream()
                .collect(Collectors.toMap(ResolvedLine::materialId, Function.identity()));
        Map<Long, BigDecimal> totals = new HashMap<>();
        Set<MaterialLocationKey> uniqueKeys = new HashSet<>();
        List<PlannedAllocation> result = new ArrayList<>();
        for (ClaimPreviewAllocationDTO allocation : input) {
            Long materialId = Ids.parse(allocation.materialId());
            Long locationId = Ids.parse(allocation.locationId());
            ResolvedLine line = lineMap.get(materialId);
            StockLocation location = locationMap.get(locationId);
            if (line == null) {
                throw badRequest("计划分配物料不属于本次申领：" + materialId);
            }
            if (location == null) {
                throw badRequest("计划分配库位不存在或已停用：" + locationId);
            }
            if (!uniqueKeys.add(new MaterialLocationKey(materialId, locationId))) {
                throw badRequest("同一物料在同一库位只能分配一次：" + materialId + "/" + locationId);
            }
            if (line.material().getMaterialType() == MaterialType.FIXED_ASSET
                    && !isInteger(allocation.plannedQuantity())) {
                throw badRequest("固定资产计划分配数量必须为整数：" + materialId);
            }
            totals.merge(materialId, allocation.plannedQuantity(), BigDecimal::add);
            result.add(new PlannedAllocation(line, location, allocation.plannedQuantity(),
                    line.availabilityByLocation().get(locationId)));
        }
        for (ResolvedLine line : lines) {
            BigDecimal total = totals.getOrDefault(line.materialId(), BigDecimal.ZERO);
            if (total.compareTo(line.requestedQuantity()) != 0) {
                throw badRequest("物料计划分配数量合计必须等于申请数量：" + line.materialId());
            }
        }
        return result;
    }

    private List<ClaimShortageVO> shortages(List<ResolvedLine> lines,
                                            List<PlannedAllocation> planned,
                                            boolean noLocations) {
        if (noLocations) {
            return lines.stream()
                    .map(line -> toShortageVO(line, null, line.requestedQuantity(), BigDecimal.ZERO))
                    .toList();
        }
        List<ClaimShortageVO> result = new ArrayList<>();
        for (PlannedAllocation allocation : planned) {
            BigDecimal available = allocation.availability().availableQuantity();
            if (allocation.plannedQuantity().compareTo(available) > 0) {
                result.add(toShortageVO(allocation.line(), allocation.location(),
                        allocation.plannedQuantity(), available));
            }
        }
        return result;
    }

    private ClaimAvailabilityVO toAvailabilityVO(Material material,
                                                 ItemTimeService.BookingWindow bookingWindow,
                                                 Instant asOf,
                                                 Long excludeClaimId,
                                                 List<StockLocation> locations) {
        StockSnapshot snapshot = availabilityService.loadSnapshot(material, bookingWindow, asOf,
                excludeClaimId, false);
        BigDecimal availableQuantity = locations.stream()
                .map(location -> availabilityService.availability(snapshot, location.getId()).availableQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ownedQuantity = material.getMaterialType() == MaterialType.FIXED_ASSET
                ? BigDecimal.valueOf(snapshot.ownedAssetTotal())
                : snapshot.balances().stream()
                .map(StockBalance::getOwnedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ClaimAvailabilityVO(str(material.getId()), material.getMaterialName(), material.getSpecification(),
                material.getMaterialType().name(), material.getQuantityMode(), material.getBaseUnit(),
                ownedQuantity, availableQuantity);
    }

    private ClaimPreviewLineVO toLineVO(ResolvedLine line) {
        BigDecimal totalAvailable = line.availabilityByLocation().values().stream()
                .map(LocationAvailability::availableQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Material material = line.material();
        return new ClaimPreviewLineVO(str(line.materialId()), material.getMaterialName(), material.getSpecification(),
                material.getMaterialType(), material.getQuantityMode(), material.getBaseUnit(), line.requestedQuantity(),
                totalAvailable, line.snapshot().reservationRule(), line.snapshot().reservationWindow().startAt(),
                line.snapshot().reservationWindow().endAt(), line.snapshot().ownedAssetTotal());
    }

    private ClaimPlannedAllocationVO toAllocationVO(PlannedAllocation allocation) {
        Material material = allocation.line().material();
        StockLocation location = allocation.location();
        return new ClaimPlannedAllocationVO(str(allocation.line().materialId()), material.getMaterialName(),
                str(location.getId()), location.getLocationCode(), location.getLocationName(),
                allocation.plannedQuantity(), allocation.availability().availableQuantity());
    }

    private ClaimShortageVO toShortageVO(ResolvedLine line, StockLocation location,
                                         BigDecimal requested, BigDecimal available) {
        return new ClaimShortageVO(str(line.materialId()), line.material().getMaterialName(),
                location == null ? null : str(location.getId()),
                location == null ? null : location.getLocationCode(),
                location == null ? null : location.getLocationName(), requested, available,
                requested.subtract(available).max(BigDecimal.ZERO));
    }

    private void validateRequest(ClaimPreviewDTO dto) {
        if (dto == null || dto.bookingDate() == null || !hasText(dto.startPeriodId())
                || !hasText(dto.endPeriodId()) || dto.lines() == null || dto.lines().isEmpty()) {
            throw badRequest("预约日期、起止节次和申领物料行均不能为空");
        }
        for (ClaimPreviewLineDTO line : dto.lines()) {
            if (line == null || !hasText(line.materialId())) {
                throw badRequest("申领物料行不能为空");
            }
            validateQuantity(line.requestedQuantity(), "申请数量");
        }
        if (dto.allocations() != null) {
            for (ClaimPreviewAllocationDTO allocation : dto.allocations()) {
                if (allocation == null || !hasText(allocation.materialId()) || !hasText(allocation.locationId())) {
                    throw badRequest("计划分配的物料和库位不能为空");
                }
                validateQuantity(allocation.plannedQuantity(), "计划分配数量");
            }
        }
        if (dto.purpose() != null && dto.purpose().length() > 512) {
            throw badRequest("用途长度不能超过512");
        }
    }

    private void validateQuantity(BigDecimal quantity, String label) {
        quantityValidator.requirePositiveInteger(quantity, label);
    }

    private boolean isInteger(BigDecimal quantity) {
        return quantity.stripTrailingZeros().scale() <= 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String str(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }

    private static String assetKey(Long materialId, Long locationId) {
        return materialId + ":" + locationId;
    }

    public record ApprovalPreview(
            ClaimPreviewVO preview,
            Map<String, List<Long>> candidateAssetIds
    ) {
        public List<Long> candidateAssets(Long materialId, Long locationId) {
            return candidateAssetIds.getOrDefault(assetKey(materialId, locationId), List.of());
        }
    }

    private record ResolvedLine(
            Long materialId,
            BigDecimal requestedQuantity,
            Material material,
            StockSnapshot snapshot,
            Map<Long, LocationAvailability> availabilityByLocation
    ) {
    }

    private record PlannedAllocation(
            ResolvedLine line,
            StockLocation location,
            BigDecimal plannedQuantity,
            LocationAvailability availability
    ) {
    }

    private record MaterialLocationKey(Long materialId, Long locationId) {
    }
}
