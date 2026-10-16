import request from './request'

/**
 * 助理授权接口（ASSISTANT 角色由有效授权动态计算，不持久化）
 */

/** 我的授权分页（老师）→ PageResult<GrantPageItemVO> */
export function pageMyGrants(params) {
  return request.get('/grants', { params })
}

/** 创建授权（最长一学年） */
export function createGrant(data) {
  return request.post('/grants', data)
}

/** 撤销授权（仅本人 ACTIVE 授权） */
export function revokeGrant(id) {
  return request.put(`/grants/${id}/revoke`)
}

/** 被授权学生搜索 → [{ id, realName, personNo }] */
export function searchStudentOptions(keyword) {
  return request.get('/grants/student_options', { params: { keyword } })
}

/** 授权审计分页（管理员全量）→ PageResult<GrantPageItemVO> */
export function pageGrantAudit(params) {
  return request.get('/grants/audit', { params })
}
