import request from '../request'

/**
 * 系统管理-角色权限接口（只读视图；绑定关系由 SQL 种子维护）
 */

/** 角色权限只读列表 → RoleDetailVO[] */
export function listRoles() {
  return request.get('/system/roles')
}
