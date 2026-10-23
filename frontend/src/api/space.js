import request from './request'

/** 场地模块接口封装（前缀 /api/v1/space/spaces，契约见 docs/场地模块开发文档清单.md 3.2） */

export function pageSpaces(params) {
  return request.get('/space/spaces', { params })
}

/** 场地下拉选项（供预约表单等下游模块联调；availableOnly=true 仅可用场地） */
export function listSpaceOptions(params) {
  return request.get('/space/spaces/options', { params })
}

/**
 * 按当前筛选条件导出 CSV（UTF-8 BOM，时间按上海时区展示）。
 * 返回 Blob，由调用方触发浏览器下载。
 */
export function exportSpaces(params) {
  return request.get('/space/spaces/export', { params, responseType: 'blob' })
}

/** 下载导入模板（与导出同表头的空 CSV），返回 Blob */
export function downloadImportTemplate() {
  return request.get('/space/spaces/import/template', { responseType: 'blob' })
}

/**
 * 场地批量导入（CSV/Excel，multipart 上传；部分成功）。
 * resolve SpaceImportVO：{ total, successCount, failedCount, failures:[{rowNum,labName,reason,raw}] }
 */
export function importSpaces(file, onUploadProgress) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/space/spaces/import', form, { onUploadProgress })
}

/** 批量启停（target 仅 AVAILABLE/CLOSED）→ { updated, missing } */
export function batchStatusSpaces(data) {
  return request.post('/space/spaces/batch_status', data)
}

/** 统计卡片：总块数、各状态块数与类型分布 */
export function getSpaceStats() {
  return request.get('/space/spaces/stats')
}

export function getSpaceDetail(id) {
  return request.get(`/space/spaces/${id}`)
}

export function createSpace(data) {
  return request.post('/space/spaces', data)
}

export function updateSpace(id, data) {
  return request.put(`/space/spaces/${id}`, data)
}

export function enableSpace(id) {
  return request.post(`/space/spaces/${id}/enable`)
}

export function disableSpace(id) {
  return request.post(`/space/spaces/${id}/disable`)
}

/** 停用影响评估（联动提醒）：未来未释放占用槽数与涉及预约单数 */
export function getPendingImpact(id) {
  return request.get(`/space/spaces/${id}/pending_impact`)
}

export function deleteSpace(id) {
  return request.delete(`/space/spaces/${id}`)
}
