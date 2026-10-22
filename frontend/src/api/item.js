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

// ---------- 库存预警 ----------
export function pageWarnings(params) {
  return request.get('/stock-warnings/page', { params })
}

// ---------- 库存流水 ----------
export function pageMovements(params) {
  return request.get('/stock-movements/page', { params })
}

// ---------- 库存单据 ----------
export function pageDocuments(params) {
  return request.get('/stock-documents/page', { params })
}

export function getDocument(id) {
  return request.get(`/stock-documents/${id}`)
}

export function createDocument(data) {
  return request.post('/stock-documents', data)
}

export function approveDocument(id) {
  return request.post(`/stock-documents/${id}/approve`)
}

export function postDocument(id) {
  return request.post(`/stock-documents/${id}/post`)
}

export function cancelDocument(id) {
  return request.post(`/stock-documents/${id}/cancel`)
}

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

// ---------- 物料申领 ----------
export function pageRequests(params) {
  return request.get('/material-requests/page', { params })
}

export function getRequest(id) {
  return request.get(`/material-requests/${id}`)
}

export function createRequest(data) {
  return request.post('/material-requests', data)
}

// 老师审核（第一级：草稿 → 老师已审核，逐行写批准量）
export function teacherApproveRequest(id, data) {
  return request.post(`/material-requests/${id}/teacher-approve`, data || {})
}

// 老师审核拒绝（草稿 → 已拒绝）
export function teacherRejectRequest(id) {
  return request.post(`/material-requests/${id}/teacher-reject`)
}

// 管理员终审（第二级：老师已审核 → 已批准，逐行写批准量）
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

// 学生代理代领：老师已审核通过的可代领源申领单分页
export function pageProxySources(params) {
  return request.get('/material-requests/proxy-sources', { params })
}

// 学生代理代领：源申领单行（含剩余可代领量）
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

// ---------- 物料核销 ----------
export function pageSettlements(params) {
  return request.get('/material-settlements/page', { params })
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

// 盘点快照导出（任意状态可导，已录入行带值支持中断续盘）
export function exportStocktakeSnapshot(id, format) {
  return request.get(`/stocktakes/${id}/snapshot-export`, { params: { format }, responseType: 'blob' })
}

// 盘点单列表导出（整个盘点管理维度，携带筛选条件导出全部匹配行）
export function exportStocktakes(params) {
  return request.get('/stocktakes/export', { params, responseType: 'blob' })
}

// 盘点实盘批量回导（仅盘点中状态；实盘与原因均空的行跳过）
export function importStocktakeCount(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/stocktakes/${id}/count-import`, formData)
}
