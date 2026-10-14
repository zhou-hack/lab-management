package com.labmanage.module.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.space.dto.LabCreateRequest;
import com.labmanage.module.space.dto.LabPageQuery;
import com.labmanage.module.space.dto.LabUpdateRequest;
import com.labmanage.module.space.entity.Building;
import com.labmanage.module.space.entity.Campus;
import com.labmanage.module.space.entity.Lab;
import com.labmanage.module.space.mapper.BuildingMapper;
import com.labmanage.module.space.mapper.CampusMapper;
import com.labmanage.module.space.mapper.LabMapper;
import com.labmanage.module.space.service.LabService;
import com.labmanage.module.space.vo.LabOptionVO;
import com.labmanage.module.space.vo.LabVO;
import com.labmanage.roles.department.entity.SysDepartment;
import com.labmanage.roles.department.mapper.SysDepartmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 实验室服务实现
 */
@Service
public class LabServiceImpl implements LabService {

    private final LabMapper labMapper;
    private final BuildingMapper buildingMapper;
    private final CampusMapper campusMapper;
    private final SysDepartmentMapper departmentMapper;

    public LabServiceImpl(LabMapper labMapper,
                          BuildingMapper buildingMapper,
                          CampusMapper campusMapper,
                          SysDepartmentMapper departmentMapper) {
        this.labMapper = labMapper;
        this.buildingMapper = buildingMapper;
        this.campusMapper = campusMapper;
        this.departmentMapper = departmentMapper;
    }

    @Override
    public PageResult<LabVO> page(LabPageQuery query) {
        LambdaQueryWrapper<Lab> wrapper = new LambdaQueryWrapper<>();
        if (query.getBuildingId() != null) {
            wrapper.eq(Lab::getBuildingId, query.getBuildingId());
        } else if (query.getCampusId() != null) {
            List<Long> buildingIds = buildingMapper.selectList(new LambdaQueryWrapper<Building>()
                            .eq(Building::getCampusId, query.getCampusId()))
                    .stream().map(Building::getId).toList();
            if (buildingIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(Lab::getBuildingId, buildingIds);
        }
        if (query.getDepartmentId() != null) {
            wrapper.eq(Lab::getDepartmentId, query.getDepartmentId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword();
            wrapper.and(w -> w.like(Lab::getLabCode, keyword).or().like(Lab::getLabName, keyword));
        }
        wrapper.orderByAsc(Lab::getId);
        Page<Lab> page = labMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(assemble(page.getRecords()), page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public List<LabOptionVO> options() {
        List<Lab> labs = labMapper.selectList(new LambdaQueryWrapper<Lab>()
                .eq(Lab::getEnabled, 1)
                .orderByAsc(Lab::getLabCode));
        SpaceContext context = loadContext(labs);
        return labs.stream()
                .map(lab -> {
                    Building building = context.buildings().get(lab.getBuildingId());
                    Campus campus = building != null ? context.campuses().get(building.getCampusId()) : null;
                    return new LabOptionVO(
                            lab.getId().toString(),
                            lab.getLabCode(),
                            lab.getLabName(),
                            lab.getBuildingId().toString(),
                            building != null ? building.getBuildingName() : null,
                            building != null ? building.getCampusId().toString() : null,
                            campus != null ? campus.getCampusName() : null,
                            lab.getFloorNo(),
                            lab.getRoomNo()
                    );
                })
                .toList();
    }

    @Override
    public void create(LabCreateRequest request) {
        Building building = buildingMapper.selectById(request.buildingId());
        if (building == null || building.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "所在楼栋不存在或已停用");
        }
        SysDepartment department = departmentMapper.selectById(request.departmentId());
        if (department == null || department.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "归口部门不存在或已停用");
        }
        Long codeExists = labMapper.selectCount(new LambdaQueryWrapper<Lab>()
                .eq(Lab::getLabCode, request.labCode()));
        if (codeExists > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "实验室编码已存在");
        }
        Long roomExists = labMapper.selectCount(new LambdaQueryWrapper<Lab>()
                .eq(Lab::getBuildingId, request.buildingId())
                .eq(Lab::getRoomNo, request.roomNo()));
        if (roomExists > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该楼栋房间号已存在");
        }
        Lab lab = new Lab();
        lab.setBuildingId(request.buildingId());
        lab.setDepartmentId(request.departmentId());
        lab.setLabCode(request.labCode());
        lab.setLabName(request.labName());
        lab.setLabType(request.labType());
        lab.setFloorNo(request.floorNo());
        lab.setRoomNo(request.roomNo());
        lab.setCapacity(request.capacity());
        lab.setAreaSqm(request.areaSqm());
        lab.setLocationNote(request.locationNote());
        lab.setEnabled(1);
        labMapper.insert(lab);
    }

    @Override
    public void update(String id, LabUpdateRequest request) {
        Lab lab = requireLab(id);
        lab.setLabName(request.labName());
        lab.setLabType(request.labType());
        lab.setFloorNo(request.floorNo());
        lab.setCapacity(request.capacity());
        lab.setAreaSqm(request.areaSqm());
        lab.setLocationNote(request.locationNote());
        lab.setEnabled(request.enabled());
        labMapper.updateById(lab);
    }

    @Override
    public void delete(String id) {
        Lab lab = requireLab(id);
        if (labMapper.countAssets(lab.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "实验室内存在设备，无法删除");
        }
        if (labMapper.countDataScopes(lab.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "实验室已被数据范围引用，无法删除");
        }
        labMapper.deleteById(lab.getId());
    }

    private Lab requireLab(String id) {
        Lab lab = labMapper.selectById(Ids.parse(id));
        if (lab == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "实验室不存在");
        }
        return lab;
    }

    private List<LabVO> assemble(List<Lab> labs) {
        SpaceContext context = loadContext(labs);
        return labs.stream()
                .map(lab -> {
                    Building building = context.buildings().get(lab.getBuildingId());
                    Campus campus = building != null ? context.campuses().get(building.getCampusId()) : null;
                    SysDepartment department = context.departments().get(lab.getDepartmentId());
                    return new LabVO(
                            lab.getId().toString(),
                            lab.getBuildingId().toString(),
                            building != null ? building.getBuildingCode() : null,
                            building != null ? building.getBuildingName() : null,
                            building != null ? building.getCampusId().toString() : null,
                            campus != null ? campus.getCampusCode() : null,
                            campus != null ? campus.getCampusName() : null,
                            lab.getDepartmentId().toString(),
                            department != null ? department.getDepartmentName() : null,
                            lab.getLabCode(),
                            lab.getLabName(),
                            lab.getLabType(),
                            lab.getFloorNo(),
                            lab.getRoomNo(),
                            lab.getCapacity(),
                            lab.getAreaSqm(),
                            lab.getLocationNote(),
                            lab.getEnabled()
                    );
                })
                .toList();
    }

    private SpaceContext loadContext(List<Lab> labs) {
        if (labs.isEmpty()) {
            return new SpaceContext(Map.of(), Map.of(), Map.of());
        }
        Map<Long, Building> buildings = loadMap(
                labs.stream().map(Lab::getBuildingId).collect(Collectors.toSet()),
                buildingMapper::selectBatchIds,
                Building::getId);
        Set<Long> campusIds = buildings.values().stream().map(Building::getCampusId).collect(Collectors.toSet());
        Map<Long, Campus> campuses = loadMap(campusIds, campusMapper::selectBatchIds, Campus::getId);
        Map<Long, SysDepartment> departments = loadMap(
                labs.stream().map(Lab::getDepartmentId).collect(Collectors.toSet()),
                departmentMapper::selectBatchIds,
                SysDepartment::getId);
        return new SpaceContext(buildings, campuses, departments);
    }

    private <T> Map<Long, T> loadMap(Collection<Long> ids,
                                     Function<Collection<Long>, List<T>> loader,
                                     Function<T, Long> keyGetter) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return loader.apply(ids).stream().collect(Collectors.toMap(keyGetter, Function.identity()));
    }

    /**
     * 空间关联数据上下文（避免逐行 N+1 查询）
     */
    private record SpaceContext(Map<Long, Building> buildings,
                                Map<Long, Campus> campuses,
                                Map<Long, SysDepartment> departments) {
    }
}
