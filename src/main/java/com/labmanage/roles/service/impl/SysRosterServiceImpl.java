package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.RosterBatchCreateDTO;
import com.labmanage.roles.dto.RosterCreateDTO;
import com.labmanage.roles.dto.RosterEnabledDTO;
import com.labmanage.roles.dto.RosterPageQueryDTO;
import com.labmanage.roles.dto.RosterUpdateDTO;
import com.labmanage.roles.entity.SysDepartment;
import com.labmanage.roles.entity.SysPersonRoster;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysDepartmentMapper;
import com.labmanage.roles.mapper.SysPersonRosterMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.service.SysRosterService;
import com.labmanage.roles.service.SysScopeService;
import com.labmanage.roles.vo.RosterPageItemVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预录名单管理服务实现
 */
@Service
public class SysRosterServiceImpl implements SysRosterService {

    private final SysPersonRosterMapper rosterMapper;

    private final SysDepartmentMapper departmentMapper;

    private final SysUserMapper userMapper;

    private final SysScopeService scopeService;

    public SysRosterServiceImpl(SysPersonRosterMapper rosterMapper, SysDepartmentMapper departmentMapper,
                                SysUserMapper userMapper, SysScopeService scopeService) {
        this.rosterMapper = rosterMapper;
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.scopeService = scopeService;
    }

    @Override
    public PageResult<RosterPageItemVO> page(RosterPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<RosterPageItemVO> page = rosterMapper.selectRosterPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    public void create(RosterCreateDTO dto) {
        requireEnabledDepartment(dto.departmentId());
        scopeService.requireDepartmentWrite(dto.departmentId());
        SysPersonRoster roster = new SysPersonRoster();
        roster.setPersonNo(dto.personNo());
        roster.setPersonType(dto.personType());
        roster.setRealName(dto.realName());
        roster.setPhone(dto.phone());
        roster.setDepartmentId(dto.departmentId());
        roster.setEnabled(1);
        try {
            rosterMapper.insert(roster);
        } catch (DuplicateKeyException e) {
            throw duplicateConflict(e, null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createBatch(RosterBatchCreateDTO dto) {
        requireEnabledDepartment(dto.departmentId());
        scopeService.requireDepartmentWrite(dto.departmentId());
        int row = 0;
        for (RosterBatchCreateDTO.Item item : dto.items()) {
            row++;
            SysPersonRoster roster = new SysPersonRoster();
            roster.setPersonNo(item.personNo());
            roster.setPersonType(dto.personType());
            roster.setRealName(item.realName());
            roster.setPhone(item.phone());
            roster.setDepartmentId(dto.departmentId());
            roster.setEnabled(1);
            try {
                rosterMapper.insert(roster);
            } catch (DuplicateKeyException e) {
                throw duplicateConflict(e, row);
            }
        }
        return dto.items().size();
    }

    @Override
    public void update(Long id, RosterUpdateDTO dto) {
        SysPersonRoster roster = requireRoster(id);
        scopeService.requireDepartmentWrite(roster.getDepartmentId());
        requireNotMatched(roster, "编辑");
        requireEnabledDepartment(dto.departmentId());
        // 迁入部门也须在操作者写范围内
        scopeService.requireDepartmentWrite(dto.departmentId());
        roster.setRealName(dto.realName());
        roster.setPhone(dto.phone());
        roster.setDepartmentId(dto.departmentId());
        try {
            rosterMapper.updateById(roster);
        } catch (DuplicateKeyException e) {
            throw duplicateConflict(e, null);
        }
    }

    @Override
    public void updateEnabled(Long id, RosterEnabledDTO dto) {
        SysPersonRoster roster = requireRoster(id);
        scopeService.requireDepartmentWrite(roster.getDepartmentId());
        if (Integer.valueOf(0).equals(dto.enabled())) {
            requireNotMatched(roster, "停用");
        }
        roster.setEnabled(dto.enabled());
        rosterMapper.updateById(roster);
    }

    private SysPersonRoster requireRoster(Long id) {
        SysPersonRoster roster = rosterMapper.selectById(id);
        if (roster == null) {
            throw new BusinessException(40400, "名单记录不存在");
        }
        return roster;
    }

    /** 已建档名单受保护：编辑/停用会破坏已建账号的溯源关系 */
    private void requireNotMatched(SysPersonRoster roster, String action) {
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRosterId, roster.getId()));
        if (count != null && count > 0) {
            throw new BusinessException(40900, "该名单已完成首登建档，不可" + action);
        }
    }

    private void requireEnabledDepartment(Long departmentId) {
        SysDepartment department = departmentMapper.selectById(departmentId);
        if (department == null || !Integer.valueOf(1).equals(department.getEnabled())) {
            throw new BusinessException(40000, "所属部门不存在或已停用");
        }
    }

    private BusinessException duplicateConflict(DuplicateKeyException e, Integer row) {
        String prefix = row == null ? "" : "第 " + row + " 行：";
        String message = String.valueOf(e.getMessage());
        if (message.contains("uk_sys_person_roster_no")) {
            return new BusinessException(40900, prefix + "学工号已存在");
        }
        if (message.contains("uk_sys_person_roster_phone")) {
            return new BusinessException(40900, prefix + "手机号已存在");
        }
        return new BusinessException(40900, prefix + "名单记录已存在");
    }
}
