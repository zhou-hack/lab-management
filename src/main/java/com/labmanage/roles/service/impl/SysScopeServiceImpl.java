package com.labmanage.roles.service.impl;

import com.labmanage.common.utils.SecurityUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.ScopeCreateDTO;
import com.labmanage.roles.dto.ScopePageQueryDTO;
import com.labmanage.roles.entity.SysDepartment;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.entity.SysUserDataScope;
import com.labmanage.roles.enums.RoleCode;
import com.labmanage.roles.enums.UserType;
import com.labmanage.roles.mapper.SysDepartmentMapper;
import com.labmanage.roles.mapper.SysUserDataScopeMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.service.SysScopeService;
import com.labmanage.roles.vo.AdminOptionVO;
import com.labmanage.roles.vo.ScopePageItemVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 数据范围服务实现
 */
@Service
public class SysScopeServiceImpl implements SysScopeService {

    private static final String SCOPE_ALL = "ALL";

    private static final String SCOPE_DEPARTMENT = "DEPARTMENT";

    private static final String MODE_WRITE = "WRITE";

    private final SysUserDataScopeMapper scopeMapper;

    private final SysUserMapper userMapper;

    private final SysDepartmentMapper departmentMapper;

    public SysScopeServiceImpl(SysUserDataScopeMapper scopeMapper, SysUserMapper userMapper,
                               SysDepartmentMapper departmentMapper) {
        this.scopeMapper = scopeMapper;
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
    }

    @Override
    public PageResult<ScopePageItemVO> page(ScopePageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<ScopePageItemVO> page = scopeMapper.selectScopePage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    public List<AdminOptionVO> adminOptions(String keyword) {
        return userMapper.selectAdminOptions(keyword == null ? null : keyword.trim());
    }

    @Override
    public void create(ScopeCreateDTO dto) {
        // 防提权：范围行重定义只允许全校写范围管理员操作
        requireAllWrite();
        SysUser target = userMapper.selectById(dto.userId());
        if (target == null) {
            throw new BusinessException(40400, "用户不存在");
        }
        if (!UserType.ADMIN.name().equals(target.getUserType())) {
            throw new BusinessException(40000, "仅管理员可配置数据范围");
        }
        if (!Integer.valueOf(1).equals(target.getEnabled())) {
            throw new BusinessException(40000, "目标管理员已停用");
        }
        SysUserDataScope scope = new SysUserDataScope();
        scope.setUserId(dto.userId());
        scope.setScopeType(dto.scopeType());
        scope.setAccessMode(dto.accessMode());
        scope.setEnabled(1);
        if (SCOPE_ALL.equals(dto.scopeType())) {
            if (dto.departmentId() != null) {
                throw new BusinessException(40000, "全校范围不填写目标部门");
            }
        } else {
            if (dto.departmentId() == null) {
                throw new BusinessException(40000, "部门范围必须选择目标部门");
            }
            SysDepartment department = departmentMapper.selectById(dto.departmentId());
            if (department == null || !Integer.valueOf(1).equals(department.getEnabled())) {
                throw new BusinessException(40000, "目标部门不存在或已停用");
            }
            scope.setDepartmentId(dto.departmentId());
        }
        try {
            scopeMapper.insert(scope);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "相同范围授权已存在");
        }
    }

    @Override
    public void remove(Long id) {
        requireAllWrite();
        SysUserDataScope scope = scopeMapper.selectById(id);
        if (scope == null) {
            throw new BusinessException(40400, "范围授权不存在");
        }
        // 防自锁：删除本人最后一条全校写范围后，全系统将无人能再维护范围授权
        if (Objects.equals(scope.getUserId(), currentUserId())
                && SCOPE_ALL.equals(scope.getScopeType())
                && MODE_WRITE.equals(scope.getAccessMode())) {
            Long remaining = scopeMapper.selectCount(Wrappers.<SysUserDataScope>lambdaQuery()
                    .eq(SysUserDataScope::getUserId, scope.getUserId())
                    .eq(SysUserDataScope::getScopeType, SCOPE_ALL)
                    .eq(SysUserDataScope::getAccessMode, MODE_WRITE)
                    .eq(SysUserDataScope::getEnabled, 1));
            if (remaining != null && remaining <= 1) {
                throw new BusinessException(40900, "不能删除本人最后一条全校写范围");
            }
        }
        scopeMapper.physicalDeleteById(id);
    }

    @Override
    public boolean hasAccess(Long userId, String scopeType, Long targetId, String accessMode) {
        List<SysUserDataScope> rows = scopeMapper.selectList(Wrappers.<SysUserDataScope>lambdaQuery()
                .eq(SysUserDataScope::getUserId, userId)
                .eq(SysUserDataScope::getEnabled, 1));
        for (SysUserDataScope row : rows) {
            // WRITE 请求仅 WRITE 行满足；READ 请求 READ/WRITE 行均满足
            if (MODE_WRITE.equals(accessMode) && !MODE_WRITE.equals(row.getAccessMode())) {
                continue;
            }
            if (SCOPE_ALL.equals(row.getScopeType())) {
                return true;
            }
            // 一期行目标仅部门（ALL/DEPARTMENT 之外类型待空间模块开放）
            if (Objects.equals(scopeType, row.getScopeType()) && Objects.equals(targetId, row.getDepartmentId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void requireDepartmentWrite(Long departmentId) {
        if (isOpsAdmin()) {
            return;
        }
        if (departmentId == null) {
            requireAllWrite();
            return;
        }
        if (!hasAccess(currentUserId(), SCOPE_DEPARTMENT, departmentId, MODE_WRITE)) {
            throw new BusinessException(40300, "无该部门数据写权限");
        }
    }

    @Override
    public void requireAllWrite() {
        if (isOpsAdmin()) {
            return;
        }
        if (!hasAccess(currentUserId(), SCOPE_ALL, null, MODE_WRITE)) {
            throw new BusinessException(40300, "需全校数据写权限");
        }
    }

    /**
     * OPS_ADMIN 不受数据范围约束（docs/RBAC权限管理总设计方案.md 3.2 节）；
     * 仅豁免范围写校验，接口权限码仍由 @SaCheckPermission 独立控制。
     */
    private boolean isOpsAdmin() {
        return SecurityUtils.currentRoles().contains(RoleCode.OPS_ADMIN.name());
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId();
    }
}
