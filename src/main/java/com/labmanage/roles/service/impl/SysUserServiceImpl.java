package com.labmanage.roles.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.UserCreateDTO;
import com.labmanage.roles.dto.UserEnabledDTO;
import com.labmanage.roles.dto.UserPageQueryDTO;
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

    private final BCryptPasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                              SysDepartmentMapper departmentMapper, AuthService authService,
                              BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.departmentMapper = departmentMapper;
        this.authService = authService;
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
        requireEnabledDepartment(dto.departmentId());
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
