package com.labmanage.module.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.space.dto.BuildingCreateRequest;
import com.labmanage.module.space.dto.BuildingPageQuery;
import com.labmanage.module.space.dto.BuildingUpdateRequest;
import com.labmanage.module.space.entity.Building;
import com.labmanage.module.space.entity.Campus;
import com.labmanage.module.space.mapper.BuildingMapper;
import com.labmanage.module.space.mapper.CampusMapper;
import com.labmanage.module.space.service.BuildingService;
import com.labmanage.module.space.vo.BuildingOptionVO;
import com.labmanage.module.space.vo.BuildingVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 楼栋服务实现
 */
@Service
public class BuildingServiceImpl implements BuildingService {

    private final BuildingMapper buildingMapper;
    private final CampusMapper campusMapper;

    public BuildingServiceImpl(BuildingMapper buildingMapper, CampusMapper campusMapper) {
        this.buildingMapper = buildingMapper;
        this.campusMapper = campusMapper;
    }

    @Override
    public PageResult<BuildingVO> page(BuildingPageQuery query) {
        LambdaQueryWrapper<Building> wrapper = new LambdaQueryWrapper<>();
        if (query.getCampusId() != null) {
            wrapper.eq(Building::getCampusId, query.getCampusId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword();
            wrapper.and(w -> w.like(Building::getBuildingCode, keyword).or().like(Building::getBuildingName, keyword));
        }
        wrapper.orderByAsc(Building::getId);
        Page<Building> page = buildingMapper.selectPage(PageUtils.toPage(query), wrapper);

        Map<Long, Campus> campusMap = loadCampuses(page.getRecords().stream()
                .map(Building::getCampusId).collect(Collectors.toSet()));
        List<BuildingVO> records = page.getRecords().stream()
                .map(building -> toVO(building, campusMap.get(building.getCampusId())))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public List<BuildingOptionVO> options() {
        List<Building> buildings = buildingMapper.selectList(new LambdaQueryWrapper<Building>()
                .eq(Building::getEnabled, 1)
                .orderByAsc(Building::getBuildingCode));
        Map<Long, Campus> campusMap = loadCampuses(buildings.stream()
                .map(Building::getCampusId).collect(Collectors.toSet()));
        return buildings.stream()
                .map(building -> {
                    Campus campus = campusMap.get(building.getCampusId());
                    return new BuildingOptionVO(
                            building.getId().toString(),
                            building.getCampusId().toString(),
                            campus != null ? campus.getCampusName() : null,
                            building.getBuildingCode(),
                            building.getBuildingName()
                    );
                })
                .toList();
    }

    @Override
    public void create(BuildingCreateRequest request) {
        Campus campus = campusMapper.selectById(request.campusId());
        if (campus == null || campus.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "所属校区不存在或已停用");
        }
        Long exists = buildingMapper.selectCount(new LambdaQueryWrapper<Building>()
                .eq(Building::getCampusId, request.campusId())
                .eq(Building::getBuildingCode, request.buildingCode()));
        if (exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该校区下楼栋编码已存在");
        }
        Building building = new Building();
        building.setCampusId(request.campusId());
        building.setBuildingCode(request.buildingCode());
        building.setBuildingName(request.buildingName());
        building.setEnabled(1);
        buildingMapper.insert(building);
    }

    @Override
    public void update(String id, BuildingUpdateRequest request) {
        Building building = requireBuilding(id);
        building.setBuildingName(request.buildingName());
        building.setEnabled(request.enabled());
        buildingMapper.updateById(building);
    }

    @Override
    public void delete(String id) {
        Building building = requireBuilding(id);
        if (buildingMapper.countLabs(building.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "楼栋内存在实验室，无法删除");
        }
        if (buildingMapper.countDataScopes(building.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "楼栋已被数据范围引用，无法删除");
        }
        buildingMapper.deleteById(building.getId());
    }

    private Building requireBuilding(String id) {
        Building building = buildingMapper.selectById(Ids.parse(id));
        if (building == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "楼栋不存在");
        }
        return building;
    }

    private Map<Long, Campus> loadCampuses(Collection<Long> campusIds) {
        if (campusIds.isEmpty()) {
            return Map.of();
        }
        return campusMapper.selectBatchIds(campusIds).stream()
                .collect(Collectors.toMap(Campus::getId, Function.identity()));
    }

    private BuildingVO toVO(Building building, Campus campus) {
        return new BuildingVO(
                building.getId().toString(),
                building.getCampusId().toString(),
                campus != null ? campus.getCampusCode() : null,
                campus != null ? campus.getCampusName() : null,
                building.getBuildingCode(),
                building.getBuildingName(),
                building.getEnabled()
        );
    }
}
