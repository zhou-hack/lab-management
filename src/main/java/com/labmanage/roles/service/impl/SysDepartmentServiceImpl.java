package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.roles.dto.DepartmentCreateDTO;
import com.labmanage.roles.dto.DepartmentEnabledDTO;
import com.labmanage.roles.dto.DepartmentUpdateDTO;
import com.labmanage.roles.entity.SysDepartment;
import com.labmanage.roles.entity.SysPersonRoster;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysDepartmentMapper;
import com.labmanage.roles.mapper.SysPersonRosterMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.service.SysDepartmentService;
import com.labmanage.roles.service.SysScopeService;
import com.labmanage.roles.vo.DepartmentTreeVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织部门管理服务实现
 */
@Service
public class SysDepartmentServiceImpl implements SysDepartmentService {

    private final SysDepartmentMapper departmentMapper;

    private final SysUserMapper userMapper;

    private final SysPersonRosterMapper rosterMapper;

    private final SysScopeService scopeService;

    public SysDepartmentServiceImpl(SysDepartmentMapper departmentMapper, SysUserMapper userMapper,
                                    SysPersonRosterMapper rosterMapper, SysScopeService scopeService) {
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.rosterMapper = rosterMapper;
        this.scopeService = scopeService;
    }

    @Override
    public List<DepartmentTreeVO> tree() {
        List<SysDepartment> all = departmentMapper.selectList(Wrappers.<SysDepartment>lambdaQuery()
                .orderByAsc(SysDepartment::getDepartmentCode));
        Map<Long, List<SysDepartment>> childrenOf = new HashMap<>();
        List<SysDepartment> roots = new ArrayList<>();
        for (SysDepartment dept : all) {
            if (dept.getParentId() == null) {
                roots.add(dept);
            } else {
                childrenOf.computeIfAbsent(dept.getParentId(), k -> new ArrayList<>()).add(dept);
            }
        }
        List<DepartmentTreeVO> tree = new ArrayList<>();
        for (SysDepartment root : roots) {
            tree.add(toNode(root, childrenOf));
        }
        return tree;
    }

    @Override
    public void create(DepartmentCreateDTO dto) {
        // 根部门（parentId=NULL）要求全校写范围；下级部门要求父部门写范围
        scopeService.requireDepartmentWrite(dto.parentId());
        requireEnabledParent(dto.parentId());
        // 编码留空时自动生成 DEPT_ + 4 位序号；并发取到同一序号由唯一索引兜底，重试重新取号
        boolean autoCode = !StringUtils.hasText(dto.departmentCode());
        for (int attempt = 0; ; attempt++) {
            SysDepartment dept = new SysDepartment();
            dept.setDepartmentCode(autoCode ? nextGeneratedCode() : dto.departmentCode().trim());
            dept.setDepartmentName(dto.departmentName());
            dept.setParentId(dto.parentId());
            dept.setDepartmentType(dto.departmentType());
            dept.setEnabled(1);
            try {
                departmentMapper.insert(dept);
                return;
            } catch (DuplicateKeyException e) {
                if (!autoCode) {
                    throw new BusinessException(40900, "部门编码已存在");
                }
                if (attempt >= 2) {
                    throw new BusinessException(40900, "部门编码生成冲突，请重试");
                }
            }
        }
    }

    /** 取下一个自动生成编码：历史最大 DEPT_ 序号 +1（含已删除行，编码不复用） */
    private String nextGeneratedCode() {
        String max = departmentMapper.selectMaxGeneratedCode();
        int next = max == null ? 1 : Integer.parseInt(max.substring("DEPT_".length())) + 1;
        return String.format("DEPT_%04d", next);
    }

    @Override
    public void update(Long id, DepartmentUpdateDTO dto) {
        SysDepartment dept = requireDepartment(id);
        scopeService.requireDepartmentWrite(id);
        Long newParentId = dto.parentId();
        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw new BusinessException(40000, "父部门不能是自身");
            }
            requireEnabledParent(newParentId);
            if (isDescendant(id, newParentId)) {
                throw new BusinessException(40900, "父部门不能选择自身的下级部门");
            }
        }
        dept.setDepartmentName(dto.departmentName());
        dept.setParentId(newParentId);
        dept.setDepartmentType(dto.departmentType());
        departmentMapper.updateById(dept);
    }

    @Override
    public void updateEnabled(Long id, DepartmentEnabledDTO dto) {
        SysDepartment dept = requireDepartment(id);
        scopeService.requireDepartmentWrite(id);
        if (Integer.valueOf(0).equals(dto.enabled())) {
            Long enabledChildren = departmentMapper.selectCount(Wrappers.<SysDepartment>lambdaQuery()
                    .eq(SysDepartment::getParentId, id)
                    .eq(SysDepartment::getEnabled, 1));
            if (enabledChildren != null && enabledChildren > 0) {
                throw new BusinessException(40900, "存在启用中的下级部门，请先停用下级部门");
            }
            Long users = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getDepartmentId, id));
            if (users != null && users > 0) {
                throw new BusinessException(40900, "该部门下存在用户账号，不可停用");
            }
            Long rosters = rosterMapper.selectCount(Wrappers.<SysPersonRoster>lambdaQuery()
                    .eq(SysPersonRoster::getDepartmentId, id));
            if (rosters != null && rosters > 0) {
                throw new BusinessException(40900, "该部门下存在预录名单，不可停用");
            }
        }
        dept.setEnabled(dto.enabled());
        departmentMapper.updateById(dept);
    }

    private DepartmentTreeVO toNode(SysDepartment dept, Map<Long, List<SysDepartment>> childrenOf) {
        List<DepartmentTreeVO> children = new ArrayList<>();
        for (SysDepartment child : childrenOf.getOrDefault(dept.getId(), List.of())) {
            children.add(toNode(child, childrenOf));
        }
        return new DepartmentTreeVO(
                String.valueOf(dept.getId()),
                dept.getParentId() == null ? null : String.valueOf(dept.getParentId()),
                dept.getDepartmentCode(),
                dept.getDepartmentName(),
                dept.getDepartmentType(),
                dept.getEnabled(),
                children);
    }

    private SysDepartment requireDepartment(Long id) {
        SysDepartment dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(40400, "部门不存在");
        }
        return dept;
    }

    /** 父部门须存在且启用（NULL 表示根节点，不校验） */
    private void requireEnabledParent(Long parentId) {
        if (parentId == null) {
            return;
        }
        SysDepartment parent = departmentMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(40000, "父部门不存在");
        }
        if (!Integer.valueOf(1).equals(parent.getEnabled())) {
            throw new BusinessException(40000, "父部门已停用，不能挂载新部门");
        }
    }

    /** candidate 是否位于 ancestorId 的子树内（沿 candidate 的父链向上走，命中 ancestorId 即为后代） */
    private boolean isDescendant(Long ancestorId, Long candidateId) {
        Set<Long> visited = new HashSet<>();
        Long current = candidateId;
        while (current != null && visited.add(current)) {
            if (current.equals(ancestorId)) {
                return true;
            }
            SysDepartment node = departmentMapper.selectById(current);
            current = node == null ? null : node.getParentId();
        }
        return false;
    }
}
