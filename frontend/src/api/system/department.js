import request from '../request'

/**
 * 系统管理-组织部门接口
 */

/** 部门树（含停用节点）→ DepartmentTreeVO[] */
export function getDepartmentTree() {
  return request.get('/system/departments')
}

/** 新增部门 */
export function createDepartment(data) {
  return request.post('/system/departments', data)
}

/** 编辑部门（编码不可改） */
export function updateDepartment(id, data) {
  return request.put(`/system/departments/${id}`, data)
}

/** 启用/停用（有启用子部门或被用户/名单引用时禁止停用） */
export function updateDepartmentEnabled(id, enabled) {
  return request.put(`/system/departments/${id}/enabled`, { enabled })
}
