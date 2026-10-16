package com.labmanage.module.space.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.space.dto.LabCreateDTO;
import com.labmanage.module.space.dto.LabPageQueryDTO;
import com.labmanage.module.space.dto.LabUpdateDTO;
import com.labmanage.module.space.dto.OpenRuleCreateDTO;
import com.labmanage.module.space.entity.Building;
import com.labmanage.module.space.entity.Lab;
import com.labmanage.module.space.entity.LabOpenRule;
import com.labmanage.module.space.mapper.BuildingMapper;
import com.labmanage.module.space.mapper.LabMapper;
import com.labmanage.module.space.mapper.LabOpenRuleMapper;
import com.labmanage.module.space.service.SpaceLabService;
import com.labmanage.module.space.vo.LabOpenRuleVO;
import com.labmanage.module.space.vo.LabPageItemVO;
import com.labmanage.roles.entity.SysDepartment;
import com.labmanage.roles.mapper.SysDepartmentMapper;
import com.labmanage.roles.service.SysScopeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 实验室管理服务实现（写操作按管理部门校验部门数据写范围）
 */
@Service
public class SpaceLabServiceImpl implements SpaceLabService {

    private final LabMapper labMapper;

    private final BuildingMapper buildingMapper;

    private final LabOpenRuleMapper openRuleMapper;

    private final SysDepartmentMapper departmentMapper;

    private final SysScopeService scopeService;

    public SpaceLabServiceImpl(LabMapper labMapper, BuildingMapper buildingMapper,
                               LabOpenRuleMapper openRuleMapper, SysDepartmentMapper departmentMapper,
                               SysScopeService scopeService) {
        this.labMapper = labMapper;
        this.buildingMapper = buildingMapper;
        this.openRuleMapper = openRuleMapper;
        this.departmentMapper = departmentMapper;
        this.scopeService = scopeService;
    }

    @Override
    public PageResult<LabPageItemVO> page(LabPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<LabPageItemVO> page = labMapper.selectLabPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    public void create(LabCreateDTO dto) {
        scopeService.requireDepartmentWrite(dto.departmentId());
        requireEnabledBuilding(dto.buildingId());
        requireEnabledDepartment(dto.departmentId());
        Lab lab = new Lab();
        lab.setBuildingId(dto.buildingId());
        lab.setDepartmentId(dto.departmentId());
        lab.setLabCode(dto.labCode().trim());
        fillEditable(lab, dto.labName(), dto.labType(), dto.floorNo(), dto.roomNo(),
                dto.capacity(), dto.areaSqm(), dto.locationNote());
        lab.setEnabled(1);
        try {
            labMapper.insert(lab);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "实验室编码或同楼栋房间号已存在");
        }
    }

    @Override
    public void update(Long id, LabUpdateDTO dto) {
        Lab lab = requireLab(id);
        scopeService.requireDepartmentWrite(lab.getDepartmentId());
        // 调整管理部门需同时具备新部门写范围，防止把台账划到他院规避管理
        if (!Objects.equals(lab.getDepartmentId(), dto.departmentId())) {
            scopeService.requireDepartmentWrite(dto.departmentId());
            requireEnabledDepartment(dto.departmentId());
        }
        if (!Objects.equals(lab.getBuildingId(), dto.buildingId())) {
            requireEnabledBuilding(dto.buildingId());
        }
        lab.setBuildingId(dto.buildingId());
        lab.setDepartmentId(dto.departmentId());
        fillEditable(lab, dto.labName(), dto.labType(), dto.floorNo(), dto.roomNo(),
                dto.capacity(), dto.areaSqm(), dto.locationNote());
        try {
            labMapper.updateById(lab);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "同楼栋房间号已存在");
        }
    }

    @Override
    public void updateEnabled(Long id, Integer enabled) {
        Lab lab = requireLab(id);
        scopeService.requireDepartmentWrite(lab.getDepartmentId());
        lab.setEnabled(enabled);
        labMapper.updateById(lab);
    }

    @Override
    public List<LabOpenRuleVO> listOpenRules(Long labId) {
        requireLab(labId);
        return openRuleMapper.selectByLabId(labId);
    }

    @Override
    public void createOpenRule(Long labId, OpenRuleCreateDTO dto) {
        Lab lab = requireLab(labId);
        scopeService.requireDepartmentWrite(lab.getDepartmentId());
        if (!Integer.valueOf(1).equals(lab.getEnabled())) {
            throw new BusinessException(40000, "实验室已停用，不能新增开放规则");
        }
        if (!dto.closeTime().isAfter(dto.openTime())) {
            throw new BusinessException(40000, "关闭时间必须晚于开放时间");
        }
        if (dto.validTo().isBefore(dto.validFrom())) {
            throw new BusinessException(40000, "有效期结束不能早于有效期开始");
        }
        LabOpenRule rule = new LabOpenRule();
        rule.setLabId(labId);
        rule.setWeekday(dto.weekday());
        rule.setOpenTime(dto.openTime());
        rule.setCloseTime(dto.closeTime());
        rule.setValidFrom(dto.validFrom());
        rule.setValidTo(dto.validTo());
        rule.setEnabled(1);
        openRuleMapper.insert(rule);
    }

    @Override
    public void deleteOpenRule(Long ruleId) {
        LabOpenRule rule = openRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException(40400, "开放规则不存在");
        }
        Lab lab = requireLab(rule.getLabId());
        scopeService.requireDepartmentWrite(lab.getDepartmentId());
        openRuleMapper.deleteById(ruleId);
    }

    private Lab requireLab(Long id) {
        Lab lab = labMapper.selectById(id);
        if (lab == null) {
            throw new BusinessException(40400, "实验室不存在");
        }
        return lab;
    }

    /** 楼栋须存在且启用（校区停用时楼栋应已级联停用，此处只需校验楼栋自身） */
    private void requireEnabledBuilding(Long buildingId) {
        Building building = buildingMapper.selectById(buildingId);
        if (building == null) {
            throw new BusinessException(40000, "所属楼栋不存在");
        }
        if (!Integer.valueOf(1).equals(building.getEnabled())) {
            throw new BusinessException(40000, "所属楼栋已停用，不能挂载实验室");
        }
    }

    /** 部门须存在且启用 */
    private void requireEnabledDepartment(Long departmentId) {
        SysDepartment department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BusinessException(40000, "管理部门不存在");
        }
        if (!Integer.valueOf(1).equals(department.getEnabled())) {
            throw new BusinessException(40000, "管理部门已停用，不能挂载实验室");
        }
    }

    private void fillEditable(Lab lab, String labName, String labType, String floorNo, String roomNo,
                              Integer capacity, java.math.BigDecimal areaSqm, String locationNote) {
        lab.setLabName(labName.trim());
        lab.setLabType(labType);
        lab.setFloorNo(floorNo.trim());
        lab.setRoomNo(roomNo.trim());
        lab.setCapacity(capacity);
        lab.setAreaSqm(areaSqm);
        lab.setLocationNote(locationNote == null ? null : locationNote.trim());
    }
}
