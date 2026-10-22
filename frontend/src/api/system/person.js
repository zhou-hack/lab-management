import request from '../request'

/**
 * 系统管理-统一人员视图接口（名单与账号合并，一行一人）
 */

/** 统一人员分页列表 → PageResult<PersonPageItemVO> */
export function pagePersons(params) {
  return request.get('/system/persons', { params })
}

/** 批量修改部门（名单与账号可混合，敏感操作需二次密码确认）→ 更新行数 */
export function batchUpdateDepartment(data) {
  return request.put('/system/persons/department', data)
}
