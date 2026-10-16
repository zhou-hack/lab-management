package com.labmanage.roles.service;

import com.labmanage.roles.vo.RoleDetailVO;

import java.util.List;

/**
 * 角色只读视图服务
 */
public interface SysRoleService {

    /** 全部角色及其权限、菜单绑定与绑定用户数（只读） */
    List<RoleDetailVO> listDetail();
}
