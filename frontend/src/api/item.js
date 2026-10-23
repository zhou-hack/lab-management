import request from './request'

/**
 * 物料管理模块接口层（第二阶段全量端点）
 * 响应已由 request 拦截器统一解包：成功返回 data，失败 reject（全局已提示）
 */

// ---------- 基础数据（选项） ----------
export function pageSkus(params) {
  return request.get('/material-skus/page', { params })
}

export function getMaterialSku(id) {
  return request.get(`/material-skus/${id}`)
}

export function pageLocations(params) {
  return request.get('/stock-locations/page', { params })
}

export function getStockLocation(id) {
  return request.get(`/stock-locations/${id}`)
}

// 保管人候选搜索（跨模块只读用户，库位维护用）
export function custodianOptions(keyword) {
  return request.get('/stock-locations/custodian-options', { params: { keyword: keyword || undefined } })
}

// ---------- 基础数据 CRUD ----------
// 物料分类
export function pageCategories(params) {
  return request.get('/material-categories/page', { params })
}

export function listEnabledCategories() {
  return request.get('/material-categories/list')
}

export function getCategory(id) {
  return request.get(`/material-categories/${id}`)
}

export function createCategory(data) {
  return request.post('/material-categories', data)
}

export function updateCategory(data) {
  return request.put('/material-categories', data)
}

export function updateCategoryStatus(id, enabled) {
  return request.put(`/material-categories/${id}/status`, null, { params: { enabled } })
}

export function deleteCategory(id) {
  return request.delete(`/material-categories/${id}`)
}

// 物料档案
export function createMaterialSku(data) {
  return request.post('/material-skus', data)
}

export function updateMaterialSku(data) {
  return request.put('/material-skus', data)
}

export function updateSkuStatus(id, enabled) {
  return request.put(`/material-skus/${id}/status`, null, { params: { enabled } })
}

export function deleteMaterialSku(id) {
  return request.delete(`/material-skus/${id}`)
}

// 库位
export function createStockLocation(data) {
  return request.post('/stock-locations', data)
}

export function updateStockLocation(data) {
  return request.put('/stock-locations', data)
}

export function updateLocationStatus(id, enabled) {
  return request.put(`/stock-locations/${id}/status`, null, { params: { enabled } })
}

export function deleteStockLocation(id) {
  return request.delete(`/stock-locations/${id}`)
}

// ---------- 库存余额 ----------
export function pageBalances(params) {
  return request.get('/stock-balances/page', { params })
}

export function updateBalanceWarning(id, data) {
  return request.put(`/stock-balances/${id}/warning-config`, data)
}

export function adjustStockBalance(id, data) {
  return request.post(`/stock-balances/${id}/adjust`, data)
}

// ---------- 库存预警 ----------
export function pageWarnings(params) {
  return request.get('/stock-warnings/page', { params })
}

// ---------- 库存流水 ----------
export function pageMovements(params) {
  return request.get('/stock-movements/page', { params })
}

export function createMovement(data) {
  return request.post('/stock-movements', data)
}

// ---------- 库存单据（单据处理面板 2026-10-23 下线：实发/期初导入/调整等
// 均由服务端自动审批过账，前端无人工单据处理入口；调拨/归还/领用出库/报废
// 走下方具名接口） ----------
export function createTransfer(data) {
  return request.post('/stock-documents/transfer', data)
}

export function createReturnIn(data) {
  return request.post('/stock-documents/return', data)
}

export function createScrap(data) {
  return request.post('/stock-documents/scrap', data)
}

export function createReversal(data) {
  return request.post('/stock-documents/reversal', data)
}

// ---------- 物料预约（原申领单） ----------
export function pageRequests(params) {
  return request.get('/material-requests/page', { params })
}

export function getRequest(id) {
  return request.get(`/material-requests/${id}`)
}

export function createRequest(data) {
  return request.post('/material-requests', data)
}

// 管理员终审（老师已审核 → 已批准，逐行写批准量）
export function approveRequest(id, data) {
  return request.post(`/material-requests/${id}/approve`, data || {})
}

// 管理员终审拒绝（老师已审核 → 已拒绝）
export function rejectRequest(id) {
  return request.post(`/material-requests/${id}/reject`)
}

export function cancelRequest(id) {
  return request.post(`/material-requests/${id}/cancel`)
}

export function issueRequest(id, data) {
  return request.post(`/material-requests/${id}/issue`, data)
}

// 学生代理代领源申领单分页（老师端已审核通过的老师预约单）
export function pageProxySources(params) {
  return request.get('/material-requests/proxy-sources', { params })
}

// 源申领单行（含剩余可代领量 = 批准量 − 实发量 − 已被有效代领单占用量）
export function listProxyLines(sourceRequestId) {
  return request.get(`/material-requests/proxy-sources/${sourceRequestId}/lines`)
}

// ---------- 领用定额 ----------
export function pageQuotas(params) {
  return request.get('/material-quotas/page', { params })
}

export function upsertQuota(data) {
  return request.post('/material-quotas', data)
}

export function updateQuota(id, data) {
  return request.put(`/material-quotas/${id}`, data)
}

export function enableQuota(id) {
  return request.post(`/material-quotas/${id}/enable`)
}

export function disableQuota(id) {
  return request.post(`/material-quotas/${id}/disable`)
}

// ---------- 物料预约关联场地预约（新建物料预约选择已预约课程） ----------
// 预约单候选搜索（仅待审批/已批准状态，按场地/课程/班级/用途前缀，最多 20 条）
export function bookingOptions(keyword) {
  return request.get('/material-requests/booking-options', { params: { keyword: keyword || undefined } })
}

// 老师候选搜索（老师预约选择归属老师；仅启用 TEACHER 角色，姓名前缀，最多 20 条）
export function teacherListOptions(keyword) {
  return request.get('/material-requests/teacher-options', { params: { keyword: keyword || undefined } })
}

// ---------- 物料核销 ----------
export function pageSettlements(params) {
  return request.get('/material-settlements/page', { params })
}

export function pageIssueOptions(params) {
  return request.get('/material-settlements/issue-options', { params })
}

export function createSettlement(data) {
  return request.post('/material-settlements', data)
}

// ---------- 物料批量导入/导出（xlsx / csv） ----------
// 物料档案
export function importSkus(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/material-skus/import', formData)
}

export function exportSkus(params) {
  return request.get('/material-skus/export', { params, responseType: 'blob' })
}

export function downloadSkuImportTemplate(format) {
  return request.get('/material-skus/import-template', { params: { format }, responseType: 'blob' })
}

// 物料分类
export function importCategories(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/material-categories/import', formData)
}

export function exportCategories(params) {
  return request.get('/material-categories/export', { params, responseType: 'blob' })
}

export function downloadCategoryImportTemplate(format) {
  return request.get('/material-categories/import-template', { params: { format }, responseType: 'blob' })
}

// ---------- 库存余额批量导入/导出（xlsx / csv） ----------
// 库存余额导出
export function exportBalances(params) {
  return request.get('/stock-balances/export', { params, responseType: 'blob' })
}

// 期初余额批量导入（有效行组装为一张 OPENING 期初入库草稿单）
export function importOpeningBalances(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/stock-balances/import', formData)
}

// 期初余额导入模板
export function downloadOpeningImportTemplate(format) {
  return request.get('/stock-balances/import-template', { params: { format }, responseType: 'blob' })
}
