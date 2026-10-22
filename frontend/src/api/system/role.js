import request from '../request'

/**
 * 系统管理-角色权限接口（只读视图 + 运维管理员整集替换权限绑定）
 */

/** 角色权限只读列表 → RoleDetailVO[] */
export function listRoles() {
  return request.get('/system/roles')
}

/** 全部启用权限（角色权限编辑选项） → PermissionOptionVO[] */
export function listPermissionOptions() {
  return request.get('/system/roles/permissions')
}

/** 整集替换角色权限绑定（敏感操作，需二级认证；保存后该角色持有者被踢会话） */
export function assignRolePermissions(id, permissionIds) {
  return request.put(`/system/roles/${id}/permissions`, { permissionIds })
}
