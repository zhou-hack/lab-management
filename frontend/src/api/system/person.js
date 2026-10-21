import request from '../request'

/**
 * 系统管理-统一人员视图接口（名单与账号合并，一行一人）
 */

/** 统一人员分页列表 → PageResult<PersonPageItemVO> */
export function pagePersons(params) {
  return request.get('/system/persons', { params })
}
