import request from '../request'

/**
 * 系统管理-操作日志接口（只追加，管理员审计）
 */

/** 日志分页列表 → PageResult<LogPageItemVO> */
export function pageLogs(params) {
  return request.get('/system/logs', { params })
}
