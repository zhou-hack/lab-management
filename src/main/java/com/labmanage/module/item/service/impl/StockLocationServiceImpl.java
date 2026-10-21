package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.StockLocationCreateDTO;
import com.labmanage.module.item.dto.StockLocationQuery;
import com.labmanage.module.item.dto.StockLocationUpdateDTO;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.SysUserRefMapper;
import com.labmanage.module.item.service.StockLocationService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.StockLocationVO;
import com.labmanage.module.item.vo.SysUserOptionVO;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 库位服务实现
 */
@Service
public class StockLocationServiceImpl implements StockLocationService {

    private final StockLocationMapper locationMapper;
    private final StockBalanceMapper balanceMapper;
    private final StockDocumentLineMapper documentLineMapper;
    private final SysUserRefMapper sysUserRefMapper;

    public StockLocationServiceImpl(StockLocationMapper locationMapper, StockBalanceMapper balanceMapper,
                                     StockDocumentLineMapper documentLineMapper, SysUserRefMapper sysUserRefMapper) {
        this.locationMapper = locationMapper;
        this.balanceMapper = balanceMapper;
        this.documentLineMapper = documentLineMapper;
        this.sysUserRefMapper = sysUserRefMapper;
    }

    @Override
    public PageResult<StockLocationVO> page(StockLocationQuery query) {
        LambdaQueryWrapper<StockLocation> wrapper = new LambdaQueryWrapper<>();
        if (query.getLabId() != null && !query.getLabId().isBlank()) {
            wrapper.eq(StockLocation::getLabId, IdUtils.requireId(query.getLabId(), "实验室 ID"));
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(q -> q.likeRight(StockLocation::getLocationCode, keyword)
                    .or()
                    .like(StockLocation::getLocationName, keyword));
        }
        if (query.getEnabled() != null) {
            wrapper.eq(StockLocation::getEnabled, query.getEnabled() ? 1 : 0);
        }
        wrapper.orderByAsc(StockLocation::getLocationCode);
        IPage<StockLocation> page = locationMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> names = namesOf(collectCustodianIds(page.getRecords()));
        List<StockLocationVO> records = page.getRecords().stream()
                .map(location -> StockLocationVO.from(location, names))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public void create(StockLocationCreateDTO dto) {
        Long labId = IdUtils.optionalId(dto.labId(), "实验室 ID");
        Long custodianId = IdUtils.optionalId(dto.custodianId(), "保管人 ID");
        checkCodeUnique(labId, dto.locationCode().trim(), null);
        StockLocation entity = new StockLocation();
        entity.setLabId(labId);
        entity.setLocationCode(dto.locationCode().trim());
        entity.setLocationName(dto.locationName().trim());
        entity.setLocationDetail(dto.locationDetail());
        entity.setCustodianId(custodianId);
        entity.setEnabled(1);
        try {
            locationMapper.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位码已存在，不得复用");
        }
    }

    @Override
    public void update(StockLocationUpdateDTO dto) {
        Long id = IdUtils.requireId(dto.id(), "库位 ID");
        requireLocation(id);
        Long labId = IdUtils.optionalId(dto.labId(), "实验室 ID");
        Long custodianId = IdUtils.optionalId(dto.custodianId(), "保管人 ID");
        checkCodeUnique(labId, dto.locationCode().trim(), id);
        // 全量编辑表单语义：用 UpdateWrapper 显式 set 每个字段（含可空字段），
        // 传 null 即清除（labId/custodianId），不能依赖 updateById 忽略 null 的默认策略
        try {
            locationMapper.update(null, new LambdaUpdateWrapper<StockLocation>()
                    .eq(StockLocation::getId, id)
                    .set(StockLocation::getLabId, labId)
                    .set(StockLocation::getLocationCode, dto.locationCode().trim())
                    .set(StockLocation::getLocationName, dto.locationName().trim())
                    .set(StockLocation::getLocationDetail, dto.locationDetail())
                    .set(StockLocation::getCustodianId, custodianId));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位码已存在，不得复用");
        }
    }

    @Override
    public List<SysUserOptionVO> custodianOptions(String keyword) {
        String kw = keyword == null ? null : keyword.trim();
        return sysUserRefMapper.searchOptionsByKeyword(kw).stream()
                .map(option -> new SysUserOptionVO(
                        String.valueOf(option.userId()),
                        option.realName(),
                        option.accountName(),
                        option.personNo()))
                .toList();
    }

    @Override
    public void updateStatus(String id, boolean enabled) {
        Long locationId = IdUtils.requireId(id, "库位 ID");
        requireLocation(locationId);
        StockLocation entity = new StockLocation();
        entity.setId(locationId);
        entity.setEnabled(enabled ? 1 : 0);
        locationMapper.updateById(entity);
    }

    @Override
    public StockLocationVO get(String id) {
        StockLocation location = requireLocation(IdUtils.requireId(id, "库位 ID"));
        return StockLocationVO.from(location, namesOf(collectCustodianIds(List.of(location))));
    }

    @Override
    public void delete(String id) {
        Long locationId = IdUtils.requireId(id, "库位 ID");
        requireLocation(locationId);
        if (balanceMapper.selectCount(
                new LambdaQueryWrapper<StockBalance>().eq(StockBalance::getLocationId, locationId)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该库位存在库存余额，禁止删除");
        }
        if (documentLineMapper.selectCount(new LambdaQueryWrapper<StockDocumentLine>()
                .and(w -> w.eq(StockDocumentLine::getFromLocationId, locationId)
                        .or()
                        .eq(StockDocumentLine::getToLocationId, locationId))) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该库位已有单据明细，禁止删除");
        }
        locationMapper.deleteById(locationId);
    }

    private StockLocation requireLocation(Long id) {
        StockLocation location = locationMapper.selectById(id);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在");
        }
        return location;
    }

    /** 收集库位列表中的保管人 ID（去重、剔除空值） */
    private Set<Long> collectCustodianIds(List<StockLocation> locations) {
        Set<Long> ids = new HashSet<>();
        for (StockLocation location : locations) {
            if (location.getCustodianId() != null) {
                ids.add(location.getCustodianId());
            }
        }
        return ids;
    }

    /** 批量解析用户姓名（跨模块只读；缺失用户返回空 Map，VO 姓名为空由前端回退显示 ID） */
    private Map<Long, String> namesOf(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (SysUserRefMapper.UserRef ref : sysUserRefMapper.selectNamesByIds(List.copyOf(userIds))) {
            if (ref.realName() != null) {
                names.put(ref.userId(), ref.realName());
            }
        }
        return names;
    }

    /**
     * 同实验室内库位码唯一
     */
    private void checkCodeUnique(Long labId, String code, Long excludeId) {
        LambdaQueryWrapper<StockLocation> wrapper = new LambdaQueryWrapper<StockLocation>()
                .eq(StockLocation::getLocationCode, code);
        if (labId == null) {
            wrapper.isNull(StockLocation::getLabId);
        } else {
            wrapper.eq(StockLocation::getLabId, labId);
        }
        if (excludeId != null) {
            wrapper.ne(StockLocation::getId, excludeId);
        }
        if (locationMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "库位码已存在，不得复用");
        }
    }
}
