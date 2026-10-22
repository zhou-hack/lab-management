package com.labmanage.roles.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.UserAdminRoleDTO;
import com.labmanage.roles.dto.UserCreateDTO;
import com.labmanage.roles.dto.UserEnabledDTO;
import com.labmanage.roles.dto.UserPageQueryDTO;
import com.labmanage.roles.dto.UserPasswordResetDTO;
import com.labmanage.roles.dto.UserUpdateDTO;
import com.labmanage.roles.entity.SysDepartment;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.entity.SysUserRole;
import com.labmanage.roles.enums.RoleCode;
import com.labmanage.roles.enums.UserType;
import com.labmanage.roles.mapper.SysDepartmentMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.mapper.SysUserRoleMapper;
import com.labmanage.roles.service.AuthService;
import com.labmanage.roles.service.SysScopeService;
import com.labmanage.roles.service.SysUserService;
import com.labmanage.roles.vo.DepartmentOptionVO;
import com.labmanage.roles.vo.UserDetailVO;
import com.labmanage.roles.vo.UserPageItemVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 用户管理服务实现
 */
@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;

    private final SysUserRoleMapper userRoleMapper;

    private final SysDepartmentMapper departmentMapper;

    private final AuthService authService;

    private final SysScopeService scopeService;

    private final BCryptPasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                              SysDepartmentMapper departmentMapper, AuthService authService,
                              SysScopeService scopeService, BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.departmentMapper = departmentMapper;
        this.authService = authService;
        this.scopeService = scopeService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageResult<UserPageItemVO> page(UserPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<UserPageItemVO> page = userMapper.selectUserPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    public UserDetailVO detail(Long id) {
        return toDetail(requireUser(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDetailVO createAdmin(UserCreateDTO dto) {
        requireEnabledDepartment(dto.departmentId());
        // 创建管理员须全校写，防止部门管理员制造同僚（docs/RBAC权限管理总设计方案.md 5.2）
        scopeService.requireAllWrite();
        SysUser user = new SysUser();
        user.setUserType(UserType.ADMIN.name());
        user.setAccountName(dto.accountName());
        user.setRealName(dto.realName());
        user.setDepartmentId(dto.departmentId());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        // password_changed_at 保持 NULL：新管理员首登强制改密（复用密码到期机制）
        user.setEnabled(1);
        user.setFailedLoginCount(0);
        SysUserRole userRole = new SysUserRole();
        userRole.setEnabled(1);
        try {
            userMapper.insert(user);
            userRole.setUserId(user.getId());
            userRole.setRoleId(RoleCode.ADMIN.getId());
            userRoleMapper.insert(userRole);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "登录账号已存在");
        }
        return toDetail(user);
    }

    @Override
    public UserDetailVO update(Long id, UserUpdateDTO dto) {
        SysUser user = requireUser(id);
        requireOpsAdminOperator(user);
        requireEnabledDepartment(dto.departmentId());
        // 迁出部门与迁入部门都须在操作者写范围内
        scopeService.requireDepartmentWrite(user.getDepartmentId());
        scopeService.requireDepartmentWrite(dto.departmentId());
        user.setRealName(dto.realName());
        user.setPhone(dto.phone());
        user.setDepartmentId(dto.departmentId());
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "手机号已被其他账号使用");
        }
        return toDetail(user);
    }

    @Override
    public void updateEnabled(Long id, UserEnabledDTO dto) {
        SysUser user = requireUser(id);
        requireOpsAdminOperator(user);
        scopeService.requireDepartmentWrite(user.getDepartmentId());
        if (Integer.valueOf(0).equals(dto.enabled())) {
            if (id.equals(StpUtil.getLoginIdAsLong())) {
                throw new BusinessException(40900, "不能停用当前登录账号");
            }
            user.setEnabled(0);
            userMapper.updateById(user);
            // 停用立即失效：踢出该用户全部会话
            authService.evictSession(id);
            return;
        }
        user.setEnabled(1);
        // 启用顺带解除登录锁定，避免恢复后仍处于锁定期
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, UserPasswordResetDTO dto) {
        SysUser user = requireUser(id);
        requireOpsAdminOperator(user);
        if (id.equals(StpUtil.getLoginIdAsLong())) {
            throw new BusinessException(40900, "不能重置当前登录账号密码，请使用修改密码");
        }
        scopeService.requireDepartmentWrite(user.getDepartmentId());
        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        // password_changed_at 置 NULL：复用密码到期机制，目标用户下次登录强制改密
        user.setPasswordChangedAt(null);
        userMapper.updateById(user);
        // 重置立即生效：踢出该用户全部会话
        authService.evictSession(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAdminRole(Long id, UserAdminRoleDTO dto) {
        SysUser user = requireUser(id);
        requireOpsAdminOperator(user);
        // 绑定管理员角色赋予后台全集权限，须全校写范围防部门管理员提权（同 createAdmin）
        scopeService.requireAllWrite();
        if (!UserType.TEACHER.name().equals(user.getUserType())) {
            throw new BusinessException(40000, "仅老师账号可绑定或解绑管理员角色");
        }
        // uk(user_id, role_id) 下逻辑删除行仍占位，重复绑定走恢复路径
        SysUserRole existing = userRoleMapper.selectAnyByUserAndRole(id, RoleCode.ADMIN.getId());
        if (Boolean.TRUE.equals(dto.bind())) {
            if (existing != null && Integer.valueOf(0).equals(existing.getDeleted())) {
                throw new BusinessException(40900, "已绑定管理员角色");
            }
            if (existing != null) {
                userRoleMapper.restoreById(existing.getId());
            } else {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(id);
                userRole.setRoleId(RoleCode.ADMIN.getId());
                userRole.setEnabled(1);
                userRoleMapper.insert(userRole);
            }
        } else {
            if (existing == null || !Integer.valueOf(0).equals(existing.getDeleted())) {
                throw new BusinessException(40900, "未绑定管理员角色");
            }
            userRoleMapper.deleteById(existing.getId());
        }
        // 角色变更立即生效：踢出该用户全部会话，重登按新持有角色装配
        authService.evictSession(id);
    }

    @Override
    public List<DepartmentOptionVO> departmentOptions() {
        return departmentMapper.selectList(Wrappers.<SysDepartment>lambdaQuery()
                        .eq(SysDepartment::getEnabled, 1)
                        .orderByAsc(SysDepartment::getId))
                .stream()
                .map(d -> new DepartmentOptionVO(String.valueOf(d.getId()), d.getDepartmentName()))
                .toList();
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(40400, "用户不存在");
        }
        return user;
    }

    /**
     * 运维账号保护：目标用户持有 OPS_ADMIN 角色时，仅 OPS_ADMIN 本人可编辑/启停/重置，
     * 防止普通管理员接管运维账号获取全量权限与范围豁免（docs/权限清单.md 隔离规则 8）。
     */
    private void requireOpsAdminOperator(SysUser target) {
        if (!authService.loadRoleCodes(target.getId()).contains(RoleCode.OPS_ADMIN.name())) {
            return;
        }
        if (!StpUtil.getRoleList().contains(RoleCode.OPS_ADMIN.name())) {
            throw new BusinessException(40300, "运维管理员账号仅运维管理员本人可操作");
        }
    }

    private void requireEnabledDepartment(Long departmentId) {
        SysDepartment department = departmentMapper.selectById(departmentId);
        if (department == null || !Integer.valueOf(1).equals(department.getEnabled())) {
            throw new BusinessException(40000, "所属部门不存在或已停用");
        }
    }

    private UserDetailVO toDetail(SysUser user) {
        SysDepartment department = departmentMapper.selectById(user.getDepartmentId());
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
        return new UserDetailVO(
                String.valueOf(user.getId()),
                user.getUserType(),
                user.getRealName(),
                user.getAccountName(),
                user.getPhone(),
                user.getPersonNo(),
                String.valueOf(user.getDepartmentId()),
                department == null ? null : department.getDepartmentName(),
                user.getEnabled(),
                user.getFailedLoginCount(),
                locked,
                user.getLockedUntil(),
                user.getPasswordChangedAt(),
                user.getCreateTime(),
                authService.loadRoleCodes(user.getId()));
    }
}
