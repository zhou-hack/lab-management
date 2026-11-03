package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.AssetPageQuery;
import com.labmanage.module.item.dto.ItemPageQuery;
import com.labmanage.module.item.dto.StockBalancePageQuery;
import com.labmanage.module.item.dto.StockMovementPageQuery;
import com.labmanage.module.item.dto.StockSettlementPageQuery;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialDocument;
import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.StockLocationAdmin;
import com.labmanage.module.item.entity.StockMovement;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockLocationAdminMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.StockMovementMapper;
import com.labmanage.module.item.vo.AssetSummaryVO;
import com.labmanage.module.item.vo.FixedAssetVO;
import com.labmanage.module.item.vo.StockBalanceVO;
import com.labmanage.module.item.vo.StockLocationAdminVO;
import com.labmanage.module.item.vo.StockMovementVO;
import com.labmanage.module.item.vo.StockSettlementPageItemVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockQueryService {

    private static final Map<String, String> BALANCE_SORT_COLUMNS = Map.of(
            "ownedQuantity", "owned_quantity",
            "availableQuantity", "available_quantity",
            "updateTime", "update_time"
    );
    private static final Map<String, String> ASSET_SORT_COLUMNS = Map.of(
            "assetCode", "asset_code",
            "status", "status",
            "createTime", "create_time",
            "updateTime", "update_time"
    );
    private static final Map<String, String> MOVEMENT_SORT_COLUMNS = Map.of(
            "occurredAt", "occurred_at",
            "quantity", "quantity",
            "movementType", "movement_type"
    );
    private static final Map<String, String> SETTLEMENT_SORT_COLUMNS = Map.of(
            "occurredAt", "d.occurred_at",
            "quantity", "s.quantity",
            "settlementType", "s.settlement_type"
    );

    private final StockBalanceMapper balanceMapper;
    private final StockMovementMapper movementMapper;
    private final FixedAssetMapper assetMapper;
    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final StockLocationAdminMapper locationAdminMapper;
    private final MaterialDocumentMapper documentMapper;
    private final MaterialSettlementMapper settlementMapper;
    private final SysUserMapper userMapper;

    public StockQueryService(StockBalanceMapper balanceMapper,
                             StockMovementMapper movementMapper,
                             FixedAssetMapper assetMapper,
                             MaterialMapper materialMapper,
                             StockLocationMapper locationMapper,
                             StockLocationAdminMapper locationAdminMapper,
                             MaterialDocumentMapper documentMapper,
                             MaterialSettlementMapper settlementMapper,
                             SysUserMapper userMapper) {
        this.balanceMapper = balanceMapper;
        this.movementMapper = movementMapper;
        this.assetMapper = assetMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.locationAdminMapper = locationAdminMapper;
        this.documentMapper = documentMapper;
        this.settlementMapper = settlementMapper;
        this.userMapper = userMapper;
    }

    public PageResult<StockBalanceVO> pageBalances(StockBalancePageQuery query) {
        LambdaQueryWrapper<StockBalance> wrapper = Wrappers.<StockBalance>lambdaQuery()
                .eq(query.getLocationId() != null, StockBalance::getLocationId, query.getLocationId());
        if (hasText(query.getMaterialName())) {
            List<Long> materialIds = matchingMaterialIds(query.getMaterialName(), MaterialType.CONSUMABLE);
            if (materialIds.isEmpty()) {
                return emptyPage(query);
            }
            wrapper.in(StockBalance::getMaterialId, materialIds);
        }
        applySort(wrapper, query, BALANCE_SORT_COLUMNS, StockBalance::getUpdateTime, "update_time");
        Page<StockBalance> page = balanceMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(toBalanceVOs(page.getRecords()), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    public List<StockBalanceVO> listBalancesByLocation(Long locationId) {
        List<StockBalance> balances = balanceMapper.selectList(Wrappers.<StockBalance>lambdaQuery()
                .eq(StockBalance::getLocationId, locationId)
                .orderByAsc(StockBalance::getMaterialId)
                .orderByAsc(StockBalance::getPositionText));
        return toBalanceVOs(balances);
    }

    public PageResult<FixedAssetVO> pageAssets(AssetPageQuery query) {
        LambdaQueryWrapper<FixedAsset> wrapper = Wrappers.<FixedAsset>lambdaQuery()
                .like(hasText(query.getAssetCode()), FixedAsset::getAssetCode, trim(query.getAssetCode()))
                .eq(query.getStatus() != null, FixedAsset::getStatus, query.getStatus())
                .eq(query.getLocationId() != null, FixedAsset::getLocationId, query.getLocationId());
        if (hasText(query.getMaterialName())) {
            List<Long> materialIds = matchingMaterialIds(query.getMaterialName(), MaterialType.FIXED_ASSET);
            if (materialIds.isEmpty()) {
                return emptyPage(query);
            }
            wrapper.in(FixedAsset::getMaterialId, materialIds);
        }
        if (hasText(query.getLocation())) {
            String keyword = query.getLocation().trim();
            List<Long> locationIds = locationMapper.selectList(Wrappers.<StockLocation>lambdaQuery()
                            .and(w -> w.like(StockLocation::getLocationName, keyword)
                                    .or().like(StockLocation::getLocationCode, keyword)))
                    .stream().map(StockLocation::getId).toList();
            if (locationIds.isEmpty()) {
                return emptyPage(query);
            }
            wrapper.in(FixedAsset::getLocationId, locationIds);
        }
        applySort(wrapper, query, ASSET_SORT_COLUMNS, FixedAsset::getCreateTime, "create_time");
        Page<FixedAsset> page = assetMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(toAssetVOs(page.getRecords()), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    public List<AssetSummaryVO> assetSummary() {
        return assetMapper.selectAssetSummary();
    }

    public FixedAssetVO assetDetail(Long id) {
        FixedAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "固定资产不存在");
        }
        return toAssetVOs(List.of(asset)).get(0);
    }

    public List<FixedAssetVO> listAssetsByLocation(Long locationId) {
        List<FixedAsset> assets = assetMapper.selectList(Wrappers.<FixedAsset>lambdaQuery()
                .eq(FixedAsset::getLocationId, locationId)
                .orderByAsc(FixedAsset::getAssetCode));
        return toAssetVOs(assets);
    }

    public PageResult<StockMovementVO> pageMovements(StockMovementPageQuery query) {
        validateDateRange(query.getStartDate(), query.getEndDate());
        Instant startAt = query.getStartDate() == null ? null : dayStart(query.getStartDate());
        Instant endAt = query.getEndDate() == null ? null : dayStart(query.getEndDate().plusDays(1));
        LambdaQueryWrapper<StockMovement> wrapper = Wrappers.<StockMovement>lambdaQuery()
                .eq(query.getLocationId() != null, StockMovement::getLocationId, query.getLocationId())
                .eq(hasText(query.getDirection()), StockMovement::getDirection, trim(query.getDirection()))
                .eq(hasText(query.getMovementType()), StockMovement::getMovementType, trim(query.getMovementType()))
                .ge(startAt != null, StockMovement::getOccurredAt, startAt)
                .lt(endAt != null, StockMovement::getOccurredAt, endAt);
        if (hasText(query.getMaterialName()) || query.getMaterialType() != null) {
            List<Long> materialIds = matchingMaterialIds(query.getMaterialName(), query.getMaterialType());
            if (materialIds.isEmpty()) {
                return emptyPage(query);
            }
            wrapper.in(StockMovement::getMaterialId, materialIds);
        }
        applySort(wrapper, query, MOVEMENT_SORT_COLUMNS, StockMovement::getOccurredAt, "occurred_at");
        Page<StockMovement> page = movementMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<StockMovementVO> records = toMovementVOs(page.getRecords());
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    public PageResult<StockSettlementPageItemVO> pageSettlements(StockSettlementPageQuery query) {
        validateDateRange(query.getStartDate(), query.getEndDate());
        query.setDocumentNo(trim(query.getDocumentNo()));
        query.setSettlementType(trim(query.getSettlementType()));
        query.setReviewStatus(trim(query.getReviewStatus()));
        Instant startAt = query.getStartDate() == null ? null : dayStart(query.getStartDate());
        Instant endAt = query.getEndDate() == null ? null : dayStart(query.getEndDate().plusDays(1));
        String sortColumn = query.getSortBy() == null ? "d.occurred_at"
                : SETTLEMENT_SORT_COLUMNS.getOrDefault(query.getSortBy(), "d.occurred_at");
        String sortDirection = "asc".equalsIgnoreCase(query.getSortOrder()) ? "ASC" : "DESC";
        Page<MaterialSettlement> page = settlementMapper.selectSettlementPage(
                PageUtils.toPage(query), query, startAt, endAt, sortColumn, sortDirection);
        List<StockSettlementPageItemVO> records = toSettlementPageItemVOs(page.getRecords());
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private List<StockBalanceVO> toBalanceVOs(List<StockBalance> balances) {
        if (balances.isEmpty()) {
            return List.of();
        }
        Map<Long, Material> materials = loadMaterials(balances.stream().map(StockBalance::getMaterialId).collect(Collectors.toSet()));
        Map<Long, StockLocation> locations = loadLocations(balances.stream().map(StockBalance::getLocationId).collect(Collectors.toSet()));
        return balances.stream().map(balance -> {
            Material material = materials.get(balance.getMaterialId());
            StockLocation location = locations.get(balance.getLocationId());
            return new StockBalanceVO(str(balance.getId()), str(balance.getMaterialId()),
                    material == null ? null : material.getMaterialName(),
                    material == null ? null : material.getSpecification(),
                    material == null ? null : material.getBaseUnit(), str(balance.getLocationId()),
                    location == null ? null : location.getLocationCode(),
                    location == null ? null : location.getLocationName(), balance.getPositionText(),
                    balance.getOwnedQuantity(), balance.getOwnedQuantity().subtract(balance.getAvailableQuantity()),
                    balance.getAvailableQuantity(), balance.getVersion(), balance.getUpdateTime());
        }).toList();
    }

    private List<FixedAssetVO> toAssetVOs(List<FixedAsset> assets) {
        if (assets.isEmpty()) {
            return List.of();
        }
        Map<Long, Material> materials = loadMaterials(assets.stream().map(FixedAsset::getMaterialId).collect(Collectors.toSet()));
        Set<Long> locationIds = assets.stream().map(FixedAsset::getLocationId).collect(Collectors.toSet());
        Map<Long, StockLocation> locations = loadLocations(locationIds);
        Map<Long, List<StockLocationAdminVO>> custodians = loadLocationAdmins(locationIds);
        return assets.stream().map(asset -> {
            Material material = materials.get(asset.getMaterialId());
            StockLocation location = locations.get(asset.getLocationId());
            return new FixedAssetVO(str(asset.getId()), str(asset.getMaterialId()),
                    material == null ? null : material.getMaterialName(),
                    material == null ? null : material.getSpecification(),
                    material == null ? null : material.getBaseUnit(), asset.getAssetCode(), asset.getPrice(),
                    str(asset.getLocationId()), location == null ? null : location.getLocationCode(),
                    location == null ? null : location.getLocationName(), asset.getPositionText(), asset.getStatus(),
                    custodians.getOrDefault(asset.getLocationId(), List.of()),
                    asset.getCreateTime(), asset.getUpdateTime());
        }).toList();
    }

    private Map<Long, List<StockLocationAdminVO>> loadLocationAdmins(Set<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        List<StockLocationAdmin> relations = locationAdminMapper.selectList(
                Wrappers.<StockLocationAdmin>lambdaQuery()
                        .in(StockLocationAdmin::getLocationId, locationIds)
                        .orderByAsc(StockLocationAdmin::getLocationId)
                        .orderByAsc(StockLocationAdmin::getUserId));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysUser> users = loadUsers(relations.stream()
                .map(StockLocationAdmin::getUserId).collect(Collectors.toSet()));
        Map<Long, List<StockLocationAdminVO>> result = new HashMap<>();
        for (StockLocationAdmin relation : relations) {
            SysUser user = users.get(relation.getUserId());
            if (user != null) {
                result.computeIfAbsent(relation.getLocationId(), key -> new ArrayList<>())
                        .add(new StockLocationAdminVO(str(user.getId()), user.getRealName(), user.getAccountName()));
            }
        }
        return result;
    }

    private List<StockMovementVO> toMovementVOs(List<StockMovement> movements) {
        if (movements.isEmpty()) {
            return List.of();
        }
        Map<Long, Material> materials = loadMaterials(movements.stream().map(StockMovement::getMaterialId).collect(Collectors.toSet()));
        Map<Long, StockLocation> locations = loadLocations(movements.stream().map(StockMovement::getLocationId).collect(Collectors.toSet()));
        Set<Long> assetIds = movements.stream().map(StockMovement::getFixedAssetId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, FixedAsset> assets = assetIds.isEmpty() ? Map.of() : assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(FixedAsset::getId, Function.identity()));
        Set<Long> documentIds = movements.stream().map(StockMovement::getMaterialDocumentId).collect(Collectors.toSet());
        Map<Long, MaterialDocument> documents = documentIds.isEmpty() ? Map.of() : documentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(MaterialDocument::getId, Function.identity()));
        Map<Long, SysUser> users = loadUsers(movements.stream().map(StockMovement::getOperatorId).collect(Collectors.toSet()));
        return movements.stream().map(movement -> {
            Material material = materials.get(movement.getMaterialId());
            FixedAsset asset = movement.getFixedAssetId() == null ? null : assets.get(movement.getFixedAssetId());
            StockLocation location = locations.get(movement.getLocationId());
            MaterialDocument document = documents.get(movement.getMaterialDocumentId());
            SysUser operator = users.get(movement.getOperatorId());
            return new StockMovementVO(str(movement.getId()), movement.getMovementKey(), movement.getMovementType(),
                    movement.getDirection(), str(movement.getMaterialId()),
                    material == null ? null : material.getMaterialName(),
                    material == null ? null : material.getMaterialType(),
                    material == null ? null : material.getBaseUnit(), str(movement.getFixedAssetId()),
                    asset == null ? null : asset.getAssetCode(), str(movement.getLocationId()),
                    location == null ? null : location.getLocationCode(),
                    location == null ? null : location.getLocationName(), movement.getQuantity(),
                    str(movement.getMaterialDocumentId()), document == null ? null : document.getDocumentNo(),
                    str(movement.getMaterialDocumentLineId()), str(movement.getOperatorId()),
                    operator == null ? null : operator.getRealName(), movement.getOccurredAt(),
                    movement.getBeforeOwnedQuantity(), movement.getAfterOwnedQuantity(),
                    movement.getBeforeAvailableQuantity(), movement.getAfterAvailableQuantity());
        }).toList();
    }

    private List<StockSettlementPageItemVO> toSettlementPageItemVOs(List<MaterialSettlement> settlements) {
        if (settlements.isEmpty()) {
            return List.of();
        }
        Set<Long> documentIds = settlements.stream()
                .map(MaterialSettlement::getMaterialDocumentId).collect(Collectors.toSet());
        Map<Long, MaterialDocument> documents = documentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(MaterialDocument::getId, Function.identity()));
        Map<Long, Material> materials = loadMaterials(settlements.stream()
                .map(MaterialSettlement::getMaterialId).collect(Collectors.toSet()));
        Set<Long> assetIds = settlements.stream().map(MaterialSettlement::getFixedAssetId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, FixedAsset> assets = assetIds.isEmpty() ? Map.of() : assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(FixedAsset::getId, Function.identity()));
        Set<Long> locationIds = documents.values().stream().map(MaterialDocument::getLocationId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, StockLocation> locations = loadLocations(locationIds);
        Set<Long> userIds = documents.values().stream().map(MaterialDocument::getOperatorId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        userIds.addAll(documents.values().stream().map(MaterialDocument::getReviewedBy)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, SysUser> users = loadUsers(userIds);
        return settlements.stream().map(settlement -> {
            MaterialDocument document = documents.get(settlement.getMaterialDocumentId());
            Material material = materials.get(settlement.getMaterialId());
            FixedAsset asset = settlement.getFixedAssetId() == null ? null : assets.get(settlement.getFixedAssetId());
            StockLocation location = document == null || document.getLocationId() == null ? null
                    : locations.get(document.getLocationId());
            SysUser operator = document == null || document.getOperatorId() == null ? null
                    : users.get(document.getOperatorId());
            SysUser reviewer = document == null || document.getReviewedBy() == null ? null
                    : users.get(document.getReviewedBy());
            return new StockSettlementPageItemVO(
                    str(settlement.getId()),
                    str(settlement.getMaterialDocumentId()),
                    document == null ? null : document.getDocumentNo(),
                    document == null ? null : document.getBusinessScene(),
                    str(settlement.getSourceMaterialDocumentLineId()),
                    str(settlement.getMaterialId()),
                    material == null ? null : material.getMaterialName(),
                    material == null ? null : material.getSpecification(),
                    material == null ? null : material.getBaseUnit(),
                    str(settlement.getFixedAssetId()),
                    asset == null ? null : asset.getAssetCode(),
                    settlement.getSettlementType(),
                    settlement.getQuantity(),
                    settlement.getTargetAssetStatus(),
                    settlement.getReason(),
                    document == null ? null : str(document.getLocationId()),
                    location == null ? null : location.getLocationCode(),
                    location == null ? null : location.getLocationName(),
                    document == null ? null : str(document.getOperatorId()),
                    operator == null ? null : operator.getRealName(),
                    document == null ? null : document.getReviewStatus(),
                    document == null ? null : str(document.getReviewedBy()),
                    reviewer == null ? null : reviewer.getRealName(),
                    document == null ? null : document.getReviewedAt(),
                    document == null ? null : document.getOccurredAt(),
                    document == null ? null : document.getPostedAt());
        }).toList();
    }

    private List<Long> matchingMaterialIds(String materialName, MaterialType materialType) {
        return materialMapper.selectIdsByNameAndTypeIncludingDeleted(trim(materialName), materialType);
    }

    private Map<Long, Material> loadMaterials(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : materialMapper.selectBatchIdsIncludingDeleted(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private Map<Long, StockLocation> loadLocations(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : locationMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StockLocation::getId, Function.identity()));
    }

    private Map<Long, SysUser> loadUsers(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private <T> void applySort(LambdaQueryWrapper<T> wrapper, ItemPageQuery query,
                               Map<String, String> whitelist, SFunction<T, ?> defaultColumn,
                               String defaultColumnName) {
        String column = query.getSortBy() == null ? null : whitelist.get(query.getSortBy());
        if (column == null) {
            wrapper.orderByDesc(defaultColumn);
            return;
        }
        wrapper.last("ORDER BY " + column + ("asc".equalsIgnoreCase(query.getSortOrder()) ? " ASC" : " DESC")
                + (column.equals(defaultColumnName) ? "" : ", " + defaultColumnName + " DESC"));
    }

    private <T> PageResult<T> emptyPage(ItemPageQuery query) {
        return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "开始日期不能晚于结束日期");
        }
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(ItemTimeService.SHANGHAI).toInstant();
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String str(Long id) { return id == null ? null : String.valueOf(id); }
}
