package com.labmanage.module.space.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.space.dto.CampusCreateDTO;
import com.labmanage.module.space.dto.CampusPageQueryDTO;
import com.labmanage.module.space.dto.CampusUpdateDTO;
import com.labmanage.module.space.entity.Building;
import com.labmanage.module.space.entity.Campus;
import com.labmanage.module.space.mapper.BuildingMapper;
import com.labmanage.module.space.mapper.CampusMapper;
import com.labmanage.module.space.service.SpaceCampusService;
import com.labmanage.module.space.vo.CampusOptionVO;
import com.labmanage.module.space.vo.CampusPageItemVO;
import com.labmanage.roles.service.SysScopeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 校区管理服务实现（写操作要求全校数据写范围）
 */
@Service
public class SpaceCampusServiceImpl implements SpaceCampusService {

    private final CampusMapper campusMapper;

    private final BuildingMapper buildingMapper;

    private final SysScopeService scopeService;

    public SpaceCampusServiceImpl(CampusMapper campusMapper, BuildingMapper buildingMapper,
                                  SysScopeService scopeService) {
        this.campusMapper = campusMapper;
        this.buildingMapper = buildingMapper;
        this.scopeService = scopeService;
    }

    @Override
    public PageResult<CampusPageItemVO> page(CampusPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<CampusPageItemVO> page = campusMapper.selectCampusPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    public List<CampusOptionVO> options() {
        return campusMapper.selectEnabledOptions();
    }

    @Override
    public void create(CampusCreateDTO dto) {
        scopeService.requireAllWrite();
        Campus campus = new Campus();
        campus.setCampusCode(dto.campusCode().trim());
        campus.setCampusName(dto.campusName().trim());
        campus.setAddress(dto.address() == null ? null : dto.address().trim());
        campus.setEnabled(1);
        try {
            campusMapper.insert(campus);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "校区编码已存在");
        }
    }

    @Override
    public void update(Long id, CampusUpdateDTO dto) {
        Campus campus = requireCampus(id);
        scopeService.requireAllWrite();
        campus.setCampusName(dto.campusName().trim());
        campus.setAddress(dto.address() == null ? null : dto.address().trim());
        campusMapper.updateById(campus);
    }

    @Override
    public void updateEnabled(Long id, Integer enabled) {
        Campus campus = requireCampus(id);
        scopeService.requireAllWrite();
        if (Integer.valueOf(0).equals(enabled)) {
            Long enabledBuildings = buildingMapper.selectCount(Wrappers.<Building>lambdaQuery()
                    .eq(Building::getCampusId, id)
                    .eq(Building::getEnabled, 1));
            if (enabledBuildings != null && enabledBuildings > 0) {
                throw new BusinessException(40900, "存在启用中的楼栋，请先停用楼栋");
            }
        }
        campus.setEnabled(enabled);
        campusMapper.updateById(campus);
    }

    private Campus requireCampus(Long id) {
        Campus campus = campusMapper.selectById(id);
        if (campus == null) {
            throw new BusinessException(40400, "校区不存在");
        }
        return campus;
    }
}
