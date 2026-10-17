package com.labmanage.module.space.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.space.dto.BuildingCreateDTO;
import com.labmanage.module.space.dto.BuildingPageQueryDTO;
import com.labmanage.module.space.dto.BuildingUpdateDTO;
import com.labmanage.module.space.entity.Building;
import com.labmanage.module.space.entity.Campus;
import com.labmanage.module.space.entity.Lab;
import com.labmanage.module.space.mapper.BuildingMapper;
import com.labmanage.module.space.mapper.CampusMapper;
import com.labmanage.module.space.mapper.LabMapper;
import com.labmanage.module.space.service.SpaceBuildingService;
import com.labmanage.module.space.vo.BuildingOptionVO;
import com.labmanage.module.space.vo.BuildingPageItemVO;
import com.labmanage.roles.service.SysScopeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 楼栋管理服务实现（写操作要求全校数据写范围）
 */
@Service
public class SpaceBuildingServiceImpl implements SpaceBuildingService {

    private final BuildingMapper buildingMapper;

    private final CampusMapper campusMapper;

    private final LabMapper labMapper;

    private final SysScopeService scopeService;

    public SpaceBuildingServiceImpl(BuildingMapper buildingMapper, CampusMapper campusMapper,
                                    LabMapper labMapper, SysScopeService scopeService) {
        this.buildingMapper = buildingMapper;
        this.campusMapper = campusMapper;
        this.labMapper = labMapper;
        this.scopeService = scopeService;
    }

    @Override
    public PageResult<BuildingPageItemVO> page(BuildingPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<BuildingPageItemVO> page = buildingMapper.selectBuildingPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    public List<BuildingOptionVO> options(Long campusId) {
        return buildingMapper.selectEnabledOptions(campusId);
    }

    @Override
    public void create(BuildingCreateDTO dto) {
        scopeService.requireAllWrite();
        requireEnabledCampus(dto.campusId());
        Building building = new Building();
        building.setCampusId(dto.campusId());
        building.setBuildingCode(dto.buildingCode().trim());
        building.setBuildingName(dto.buildingName().trim());
        building.setEnabled(1);
        try {
            buildingMapper.insert(building);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "该校区下楼栋编码已存在");
        }
    }

    @Override
    public void update(Long id, BuildingUpdateDTO dto) {
        Building building = requireBuilding(id);
        scopeService.requireAllWrite();
        if (!building.getCampusId().equals(dto.campusId())) {
            requireEnabledCampus(dto.campusId());
        }
        building.setCampusId(dto.campusId());
        building.setBuildingName(dto.buildingName().trim());
        try {
            buildingMapper.updateById(building);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "该校区下楼栋编码已存在");
        }
    }

    @Override
    public void updateEnabled(Long id, Integer enabled) {
        Building building = requireBuilding(id);
        scopeService.requireAllWrite();
        if (Integer.valueOf(0).equals(enabled)) {
            Long enabledLabs = labMapper.selectCount(Wrappers.<Lab>lambdaQuery()
                    .eq(Lab::getBuildingId, id)
                    .eq(Lab::getEnabled, 1));
            if (enabledLabs != null && enabledLabs > 0) {
                throw new BusinessException(40900, "存在启用中的实验室，请先停用实验室");
            }
        }
        building.setEnabled(enabled);
        buildingMapper.updateById(building);
    }

    private Building requireBuilding(Long id) {
        Building building = buildingMapper.selectById(id);
        if (building == null) {
            throw new BusinessException(40400, "楼栋不存在");
        }
        return building;
    }

    /** 校区须存在且启用 */
    private void requireEnabledCampus(Long campusId) {
        Campus campus = campusMapper.selectById(campusId);
        if (campus == null) {
            throw new BusinessException(40000, "所属校区不存在");
        }
        if (!Integer.valueOf(1).equals(campus.getEnabled())) {
            throw new BusinessException(40000, "所属校区已停用，不能挂载楼栋");
        }
    }
}
