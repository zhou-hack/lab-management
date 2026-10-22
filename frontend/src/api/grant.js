import request from './request'

/** 助理授权接口封装（前缀 /api/v1/system/grants，权限 system:grant:*） */

/** 本人授权的助理分页列表 */
export function pageMyGrants(params) {
  return request.get('/system/grants', { params })
}

/** 按学号/姓名模糊搜索可授权学生（需 system:grant:create） */
export function searchGrantStudents(params) {
  return request.get('/system/grants/students', { params })
}

/** 创建助理授权（即时生效） */
export function createGrant(data) {
  return request.post('/system/grants', data)
}

/** 撤销助理授权（仅本人、仅生效中） */
export function revokeGrant(id) {
  return request.post(`/system/grants/${id}/revoke`)
}
