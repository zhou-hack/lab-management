package com.labmanage.roles.service;

import com.labmanage.roles.dto.RolePermissionAssignDTO;
import com.labmanage.roles.vo.PermissionOptionVO;
import com.labmanage.roles.vo.RoleDetailVO;
import com.labmanage.roles.vo.RoleUserItemVO;

import java.util.List;

/**
 * 角色只读视图与角色权限绑定维护服务
 */
public interface SysRoleService {

    /** 全部角色及其权限、菜单绑定与绑定用户数（只读） */
    List<RoleDetailVO> listDetail();

    /** 全部启用权限（角色权限编辑选项） */
    List<PermissionOptionVO> listPermissionOptions();

    /** 角色下用户名单（ASSISTANT 由有效助理授权动态计算，其余读持久化绑定） */
    List<RoleUserItemVO> listRoleUsers(Long roleId);

    /**
     * 整集替换角色权限绑定（OPS_ADMIN 角色锁定；变更后踢出该角色全部持有者会话，
     * 含 ASSISTANT 动态持有者，重新登录生效）。
     */
    void assignPermissions(Long roleId, RolePermissionAssignDTO dto);
}
