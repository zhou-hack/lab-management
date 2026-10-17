package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.UserCreateDTO;
import com.labmanage.roles.dto.UserEnabledDTO;
import com.labmanage.roles.dto.UserPageQueryDTO;
import com.labmanage.roles.dto.UserPasswordResetDTO;
import com.labmanage.roles.dto.UserUpdateDTO;
import com.labmanage.roles.vo.DepartmentOptionVO;
import com.labmanage.roles.vo.UserDetailVO;
import com.labmanage.roles.vo.UserPageItemVO;

import java.util.List;

/**
 * 用户管理服务
 */
public interface SysUserService {

    /** 用户分页列表（关键字/类型/状态筛选） */
    PageResult<UserPageItemVO> page(UserPageQueryDTO query);

    /** 用户详情（含角色） */
    UserDetailVO detail(Long id);

    /** 创建管理员账号并绑定 ADMIN 角色；账号冲突抛 40900 */
    UserDetailVO createAdmin(UserCreateDTO dto);

    /** 编辑用户资料（账号、学工号、用户类型不可改）；手机号冲突抛 40900 */
    UserDetailVO update(Long id, UserUpdateDTO dto);

    /** 启用/停用：停用踢出会话；启用顺带解除登录锁定。不允许停用当前登录账号 */
    void updateEnabled(Long id, UserEnabledDTO dto);

    /** 重置密码：置为指定初始密码、强制首登改密并踢出全部会话；不允许重置当前登录账号 */
    void resetPassword(Long id, UserPasswordResetDTO dto);

    /** 启用部门下拉选项（用户管理表单用） */
    List<DepartmentOptionVO> departmentOptions();
}
