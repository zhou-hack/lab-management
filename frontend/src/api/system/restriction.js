import request from '../request'

/**
 * 系统管理-预约限制接口（时间内禁止指定 EQUIPMENT/LAB/ALL 预约）
 */

/** 限制分页列表 → PageResult<RestrictionPageItemVO> */
export function pageRestrictions(params) {
  return request.get('/system/restrictions', { params })
}

/** 被限制用户下拉 → UserOptionVO[] */
export function getRestrictionUserOptions(keyword) {
  return request.get('/system/restrictions/user_options', { params: { keyword } })
}

/** 手工新增限制（endAt 为空表示直至撤销） */
export function createRestriction(data) {
  return request.post('/system/restrictions', data)
}

/** 撤销限制 */
export function revokeRestriction(id) {
  return request.put(`/system/restrictions/${id}/revoke`)
}
