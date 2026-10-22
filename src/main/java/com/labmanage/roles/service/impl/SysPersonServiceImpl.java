package com.labmanage.roles.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.PersonBatchDepartmentDTO;
import com.labmanage.roles.dto.PersonPageQueryDTO;
import com.labmanage.roles.entity.SysDepartment;
import com.labmanage.roles.entity.SysPersonRoster;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.enums.RoleCode;
import com.labmanage.roles.mapper.SysDepartmentMapper;
import com.labmanage.roles.mapper.SysPersonRosterMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.service.AuthService;
import com.labmanage.roles.service.SysPersonService;
import com.labmanage.roles.vo.PersonPageItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一人员视图服务实现
 */
@Service
public class SysPersonServiceImpl implements SysPersonService {

    private final SysPersonRosterMapper rosterMapper;
    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;
    private final AuthService authService;

    public SysPersonServiceImpl(SysPersonRosterMapper rosterMapper,
                                SysUserMapper userMapper,
                                SysDepartmentMapper departmentMapper,
                                AuthService authService) {
        this.rosterMapper = rosterMapper;
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.authService = authService;
    }

    @Override
    public PageResult<PersonPageItemVO> page(PersonPageQueryDTO query) {
        Page<PersonPageItemVO> page = rosterMapper.selectPersonPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateDepartment(PersonBatchDepartmentDTO dto) {
        SysDepartment target = departmentMapper.selectById(dto.departmentId());
        if (target == null || !Integer.valueOf(1).equals(target.getEnabled())) {
            throw new BusinessException(40000, "目标部门不存在或已停用");
        }
        int updated = 0;
        for (PersonBatchDepartmentDTO.Item item : dto.items()) {
            if (item.userId() == null && item.rosterId() == null) {
                throw new BusinessException(40000, "存在缺少行标识的人员");
            }
            if (item.rosterId() != null) {
                SysPersonRoster roster = rosterMapper.selectById(item.rosterId());
                if (roster == null) {
                    throw new BusinessException(40400, "名单不存在");
                }
                roster.setDepartmentId(dto.departmentId());
                rosterMapper.updateById(roster);
                updated++;
            }
            if (item.userId() != null) {
                SysUser user = userMapper.selectById(item.userId());
                if (user == null) {
                    throw new BusinessException(40400, "用户不存在");
                }
                // 运维账号保护：目标持有 OPS_ADMIN 角色时仅 OPS_ADMIN 本人可调
                if (authService.loadRoleCodes(user.getId()).contains(RoleCode.OPS_ADMIN.name())
                        && !StpUtil.getRoleList().contains(RoleCode.OPS_ADMIN.name())) {
                    throw new BusinessException(40300, "运维管理员账号仅运维管理员本人可操作");
                }
                user.setDepartmentId(dto.departmentId());
                userMapper.updateById(user);
                updated++;
            }
        }
        return updated;
    }
}
