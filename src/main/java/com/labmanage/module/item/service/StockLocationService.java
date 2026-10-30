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
import com.labmanage.module.item.dto.ItemPageQuery;
import com.labmanage.module.item.dto.StockLocationAdminsDTO;
import com.labmanage.module.item.dto.StockLocationPageQuery;
import com.labmanage.module.item.dto.StockLocationResponsibleHistoryPageQuery;
import com.labmanage.module.item.dto.StockLocationUpsertDTO;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.StockLocationAdmin;
import com.labmanage.module.item.mapper.StockLocationAdminMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.vo.LocationInventoryVO;
import com.labmanage.module.item.vo.StockLocationAdminVO;
import com.labmanage.module.item.vo.StockLocationChangeVO;
import com.labmanage.module.item.vo.StockLocationOptionVO;
import com.labmanage.module.item.vo.StockLocationResponsibleHistoryVO;
import com.labmanage.module.item.vo.StockLocationVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockLocationService {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "locationCode", "location_code",
            "locationName", "location_name",
            "createTime", "create_time",
            "updateTime", "update_time"
    );

    private final StockLocationMapper locationMapper;
    private final StockLocationAdminMapper adminMapper;
    private final SysUserMapper userMapper;
    private final StockQueryService queryService;
    private final ItemTimeService timeService;
    private final StockLocationResponsibleHistoryService responsibleHistoryService;

    public StockLocationService(StockLocationMapper locationMapper,
                                StockLocationAdminMapper adminMapper,
                                SysUserMapper userMapper,
                                StockQueryService queryService,
                                ItemTimeService timeService,
                                StockLocationResponsibleHistoryService responsibleHistoryService) {
        this.locationMapper = locationMapper;
        this.adminMapper = adminMapper;
        this.userMapper = userMapper;
        this.queryService = queryService;
        this.timeService = timeService;
        this.responsibleHistoryService = responsibleHistoryService;
    }

    public PageResult<StockLocationVO> page(StockLocationPageQuery query) {
        LambdaQueryWrapper<StockLocation> wrapper = Wrappers.<StockLocation>lambdaQuery()
                .like(hasText(query.getLocationCode()), StockLocation::getLocationCode, trim(query.getLocationCode()))
                .like(hasText(query.getLocationName()), StockLocation::getLocationName, trim(query.getLocationName()))
                .eq(query.getEnabled() != null, StockLocation::getEnabled, query.getEnabled());
        applySort(wrapper, query);
        Page<StockLocation> page = locationMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, List<StockLocationAdminVO>> admins = loadAdmins(
                page.getRecords().stream().map(StockLocation::getId).collect(Collectors.toSet()));
        List<StockLocationVO> records = page.getRecords().stream()
                .map(location -> toVO(location, admins.getOrDefault(location.getId(), List.of()))).toList();
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    public StockLocationVO detail(Long id) {
        StockLocation location = requireLocation(id);
        return toVO(location, loadAdmins(Set.of(id)).getOrDefault(id, List.of()));
    }

    public List<StockLocationOptionVO> options(String keyword) {
        String value = trimToNull(keyword);
        return locationMapper.selectList(Wrappers.<StockLocation>lambdaQuery()
                        .eq(StockLocation::getEnabled, 1)
                        .and(value != null, wrapper -> wrapper.like(StockLocation::getLocationName, value)
                                .or().like(StockLocation::getLocationCode, value))
                        .orderByAsc(StockLocation::getLocationCode)
                        .last("LIMIT 100"))
                .stream()
                .map(location -> new StockLocationOptionVO(String.valueOf(location.getId()),
                        location.getLocationCode(), location.getLocationName(), location.getEnabled()))
                .toList();
    }

    public PageResult<StockLocationResponsibleHistoryVO> responsibleHistory(
            StockLocationResponsibleHistoryPageQuery query) {
        return responsibleHistoryService.page(query);
    }

    public PageResult<StockLocationChangeVO> changeHistory(StockLocationResponsibleHistoryPageQuery query) {
        Page<StockLocationChangeVO> page = locationMapper.selectChangeHistory(
                PageUtils.toPage(query), query.getLocationId());
        return PageUtils.toPageResult(page);
    }

    public List<StockLocationAdminVO> adminOptions(String keyword) {
        String value = trimToNull(keyword);
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEnabled, 1)
                .in(SysUser::getUserType, "TEACHER", "ADMIN")
                .and(value != null, wrapper -> wrapper.like(SysUser::getRealName, value)
                        .or().like(SysUser::getAccountName, value))
                .orderByAsc(SysUser::getId)
                .last("LIMIT 50"));
        return users.stream().map(user -> new StockLocationAdminVO(
                String.valueOf(user.getId()), user.getRealName(), user.getAccountName())).toList();
    }

    public LocationInventoryVO inventory(Long id) {
        StockLocation location = requireLocation(id);
        return new LocationInventoryVO(String.valueOf(id), location.getLocationCode(), location.getLocationName(),
                queryService.listBalancesByLocation(id), queryService.listAssetsByLocation(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public StockLocationVO create(StockLocationUpsertDTO dto) {
        validate(dto);
        String code = dto.locationCode().trim();
        requireCodeUnused(code, null);
        StockLocation location = new StockLocation();
        applyFields(location, dto);
        try {
            locationMapper.insert(location);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位编码已存在：" + code);
        }
        return detail(location.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public StockLocationVO update(Long id, StockLocationUpsertDTO dto) {
        validate(dto);
        StockLocation location = requireLocation(id);
        requireCodeUnused(dto.locationCode().trim(), id);
        applyFields(location, dto);
        location.setUpdateTime(timeService.now());
        location.setUpdateUserId(SecurityUtils.currentUserId());
        try {
            int rows = locationMapper.updateById(location);
            if (rows != 1) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位编码已存在：" + dto.locationCode().trim());
        }
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StockLocation location = locationMapper.selectByIdForUpdate(id);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在");
        }
        if (locationMapper.hasStockOrOpenTransfer(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位仍有库存或未完成移动，只能停用，不能删除");
        }
        int rows = locationMapper.logicalDelete(id, timeService.now(), SecurityUtils.currentUserId());
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位已变化，请刷新后重试");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public StockLocationVO updateAdmins(Long locationId, StockLocationAdminsDTO dto) {
        StockLocation location = locationMapper.selectByIdForUpdate(locationId);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在");
        }
        Set<Long> desired = new LinkedHashSet<>();
        for (String userId : dto.userIds()) {
            desired.add(Ids.parse(userId));
        }
        validateResponsibleUsers(desired);

        Map<Long, StockLocationAdmin> existing = adminMapper.selectAllByLocationIdIncludingDeleted(locationId)
                .stream().collect(Collectors.toMap(StockLocationAdmin::getUserId, Function.identity()));
        List<StockLocationAdminVO> beforeAdmins = loadAdmins(Set.of(locationId))
                .getOrDefault(locationId, List.of());
        Set<Long> current = existing.values().stream()
                .filter(relation -> Integer.valueOf(0).equals(relation.getDeleted()))
                .map(StockLocationAdmin::getUserId)
                .collect(Collectors.toSet());
        if (current.equals(desired)) {
            return toVO(location, beforeAdmins);
        }

        Long operatorId = SecurityUtils.currentUserId();
        Instant now = timeService.now();
        for (StockLocationAdmin relation : existing.values()) {
            if (desired.contains(relation.getUserId()) && Integer.valueOf(1).equals(relation.getDeleted())) {
                if (adminMapper.restore(relation.getId(), now, operatorId) != 1) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位管理员关系恢复失败，请重试");
                }
            } else if (!desired.contains(relation.getUserId()) && Integer.valueOf(0).equals(relation.getDeleted())) {
                if (adminMapper.logicalDelete(relation.getId(), now, operatorId) != 1) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位管理员关系已变化，请重试");
                }
            }
        }
        for (Long userId : desired) {
            if (existing.containsKey(userId)) {
                continue;
            }
            StockLocationAdmin relation = new StockLocationAdmin();
            relation.setId(IdWorker.getId());
            relation.setLocationId(locationId);
            relation.setUserId(userId);
            try {
                adminMapper.insert(relation);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位管理员关系已存在，请刷新后重试");
            }
        }
        List<StockLocationAdminVO> afterAdmins = loadAdmins(Set.of(locationId))
                .getOrDefault(locationId, List.of());
        responsibleHistoryService.recordChange(location, beforeAdmins, afterAdmins, operatorId, now);
        return toVO(location, afterAdmins);
    }

    private Map<Long, List<StockLocationAdminVO>> loadAdmins(Set<Long> locationIds) {
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        List<StockLocationAdmin> relations = adminMapper.selectList(Wrappers.<StockLocationAdmin>lambdaQuery()
                .in(StockLocationAdmin::getLocationId, locationIds)
                .orderByAsc(StockLocationAdmin::getLocationId)
                .orderByAsc(StockLocationAdmin::getUserId));
        if (relations.isEmpty()) {
            return Map.of();
        }
        Set<Long> userIds = relations.stream().map(StockLocationAdmin::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, List<StockLocationAdminVO>> result = new HashMap<>();
        for (StockLocationAdmin relation : relations) {
            SysUser user = users.get(relation.getUserId());
            if (user == null) {
                continue;
            }
            result.computeIfAbsent(relation.getLocationId(), key -> new ArrayList<>())
                    .add(new StockLocationAdminVO(String.valueOf(user.getId()), user.getRealName(), user.getAccountName()));
        }
        return result;
    }

    private void validateResponsibleUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, SysUser> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        for (Long userId : userIds) {
            SysUser user = users.get(userId);
            if (user == null || !Integer.valueOf(1).equals(user.getEnabled())
                    || !("ADMIN".equals(user.getUserType()) || "TEACHER".equals(user.getUserType()))) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "库位负责人必须是启用中的教师或管理员用户：" + userId);
            }
        }
    }

    private void validate(StockLocationUpsertDTO dto) {
        if (dto.locationCode() == null || dto.locationCode().isBlank()
                || dto.locationName() == null || dto.locationName().isBlank()
                || dto.enabled() == null || (dto.enabled() != 0 && dto.enabled() != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "库位编码、名称或启用状态不合法");
        }
    }

    private void applyFields(StockLocation location, StockLocationUpsertDTO dto) {
        location.setLocationCode(dto.locationCode().trim());
        location.setLocationName(dto.locationName().trim());
        location.setDescription(trimToNull(dto.description()));
        location.setEnabled(dto.enabled());
    }

    private void requireCodeUnused(String code, Long excludeId) {
        Long ownerId = locationMapper.selectIdByCode(code);
        if (ownerId != null && !ownerId.equals(excludeId)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位编码已存在：" + code);
        }
    }

    private StockLocation requireLocation(Long id) {
        StockLocation location = locationMapper.selectById(id);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在");
        }
        return location;
    }

    private StockLocationVO toVO(StockLocation location, List<StockLocationAdminVO> admins) {
        return new StockLocationVO(String.valueOf(location.getId()), location.getLocationCode(),
                location.getLocationName(), location.getDescription(), location.getEnabled(), admins,
                location.getCreateTime(), location.getUpdateTime());
    }

    private void applySort(LambdaQueryWrapper<StockLocation> wrapper, ItemPageQuery query) {
        String column = query.getSortBy() == null ? null : SORT_COLUMNS.get(query.getSortBy());
        if (column == null) {
            wrapper.orderByAsc(StockLocation::getLocationName);
            return;
        }
        wrapper.last("ORDER BY " + column + ("asc".equalsIgnoreCase(query.getSortOrder()) ? " ASC" : " DESC")
                + (column.equals("location_name") ? "" : ", location_name ASC"));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String trimToNull(String value) { return hasText(value) ? value.trim() : null; }
}
