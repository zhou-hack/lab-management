import request from '../request'

/**
 * 系统管理-名单管理接口（学生/老师账号的唯一建档来源）
 */

/** 名单分页列表 → PageResult<RosterPageItemVO> */
export function pageRosters(params) {
  return request.get('/system/rosters', { params })
}

/** 单条录入 */
export function createRoster(data) {
  return request.post('/system/rosters', data)
}

/** 批量导入（同事务，任一冲突整体回滚）→ 导入条数 */
export function createRosterBatch(data) {
  return request.post('/system/rosters/batch', data)
}

/** 编辑名单（已建档禁止编辑） */
export function updateRoster(id, data) {
  return request.put(`/system/rosters/${id}`, data)
}

/** 启用/停用（已建档禁止停用） */
export function updateRosterEnabled(id, enabled) {
  return request.put(`/system/rosters/${id}/enabled`, { enabled })
}

/** 导出名单 CSV（Blob，筛选条件同分页列表；大结果集放宽超时） */
export function exportRosters(params) {
  return request.get('/system/rosters/export', { params, responseType: 'blob', timeout: 60000 })
}

/** 下载批量导入模板 CSV（Blob） */
export function downloadRosterImportTemplate() {
  return request.get('/system/rosters/import_template', { responseType: 'blob' })
}

/** 批量导入名单 CSV（multipart，部分成功；逐行入库放宽超时）→ 导入结果 RosterImportResultVO */
export function importRosters(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/system/rosters/import', form, { timeout: 60000 })
}
