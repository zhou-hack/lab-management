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

export function approveRequest(id, data) {
  return request.post(`/material-requests/${id}/approve`, data || {})
}

export function rejectRequest(id) {
  return request.post(`/material-requests/${id}/reject`)
}

export function cancelRequest(id) {
  return request.post(`/material-requests/${id}/cancel`)
}

export function issueRequest(id, data) {
  return request.post(`/material-requests/${id}/issue`, data)
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

// ---------- 盘点 ----------
export function pageStocktakes(params) {
  return request.get('/stocktakes/page', { params })
}

export function getStocktake(id) {
  return request.get(`/stocktakes/${id}`)
}

export function createStocktake(data) {
  return request.post('/stocktakes', data)
}

export function startStocktake(id) {
  return request.post(`/stocktakes/${id}/start`)
}

export function countStocktake(id, data) {
  return request.post(`/stocktakes/${id}/count`, data)
}

export function submitReviewStocktake(id) {
  return request.post(`/stocktakes/${id}/submit-review`)
}

export function completeStocktake(id) {
  return request.post(`/stocktakes/${id}/complete`)
}

export function approveAdjustStocktake(id) {
  return request.post(`/stocktakes/${id}/approve-adjust`)
}

export function cancelStocktake(id) {
  return request.post(`/stocktakes/${id}/cancel`)
}
