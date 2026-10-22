import request from '../request'

/**
 * 系统管理-数据范围接口（管理员归口授权；无范围记录不自动授予全校数据）
 */

/** 范围授权分页列表 → PageResult<ScopePageItemVO> */
export function pageScopes(params) {
  return request.get('/system/scopes', { params })
}

/** 可授权管理员下拉 → AdminOptionVO[] */
export function getAdminOptions(keyword) {
  return request.get('/system/scopes/admin_options', { params: { keyword } })
}

/** 新增范围授权（同目标升降级通过增删行实现） */
export function createScope(data) {
  return request.post('/system/scopes', data)
}

/** 删除范围授权（物理删除，可重新授权） */
export function deleteScope(id) {
  return request.delete(`/system/scopes/${id}`)
}
