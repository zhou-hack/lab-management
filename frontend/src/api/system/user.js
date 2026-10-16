import request from '../request'

/**
 * 系统管理-用户管理接口（学生/老师账号由名单首登建档，新建仅限管理员账号）
 */

/** 用户分页列表 → PageResult<UserPageItemVO> */
export function pageUsers(params) {
  return request.get('/system/users', { params })
}

/** 用户详情（含角色）→ UserDetailVO */
export function getUser(id) {
  return request.get(`/system/users/${id}`)
}

/** 创建管理员账号（首登强制改密）→ UserDetailVO */
export function createUser(data) {
  return request.post('/system/users', data)
}

/** 编辑用户资料（账号/学工号/类型不可改）→ UserDetailVO */
export function updateUser(id, data) {
  return request.put(`/system/users/${id}`, data)
}

/** 启用/停用（停用踢出会话，启用解除锁定） */
export function updateUserEnabled(id, enabled) {
  return request.put(`/system/users/${id}/enabled`, { enabled })
}

/** 重置用户密码（强制首登改密并踢出会话；敏感操作需二级确认） */
export function resetUserPassword(id, newPassword) {
  return request.put(`/system/users/${id}/password_reset`, { newPassword })
}

/** 启用部门下拉选项 → [{ id, departmentName }] */
export function getDepartmentOptions() {
  return request.get('/system/departments/options')
}
