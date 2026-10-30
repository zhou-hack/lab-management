import request from '../request'

const stringId = (id) => String(id)

// 申领审核与实发
export function pageAdminClaims(params) {
  return request.get('/item/admin/claims', { params })
}

export function getAdminClaim(id) {
  return request.get(`/item/admin/claims/${stringId(id)}`)
}

export function checkAdminClaim(id) {
  return request.post(`/item/admin/claims/${stringId(id)}/check`)
}

export function approveAdminClaim(id, data) {
  return request.post(`/item/admin/claims/${stringId(id)}/approve`, data)
}

export function rejectAdminClaim(id, data) {
  return request.post(`/item/admin/claims/${stringId(id)}/reject`, data)
}

export function closeAdminClaim(id) {
  return request.post(`/item/admin/claims/${stringId(id)}/close`)
}

export function getClaimIssueContext(id, locationId) {
  return request.get(`/item/admin/claims/${stringId(id)}/issue_context`, {
    params: { locationId: stringId(locationId) }
  })
}

export function issueClaim(id, data) {
  return request.post(`/item/admin/claims/${stringId(id)}/issues`, data)
}

export function getClaimDocuments(id) {
  return request.get(`/item/claims/${stringId(id)}/documents`)
}

// 库存单据、申领归还与核销复核
export function pageDocuments(params) {
  return request.get('/item/documents', { params })
}

export function getDocument(id) {
  return request.get(`/item/documents/${stringId(id)}`)
}

export function getReturnContext(sourceId) {
  return request.get(`/item/documents/${stringId(sourceId)}/return_context`)
}

export function createClaimReturn(sourceId, data) {
  return request.post(`/item/documents/${stringId(sourceId)}/returns`, data)
}

export function createScrap(data) {
  return request.post('/item/documents/scrap', data)
}

export function createAdjust(data) {
  return request.post('/item/documents/adjust', data)
}

export function reviewSettlement(id, data) {
  return request.post(`/item/documents/${stringId(id)}/review`, data)
}

// 库位移动与在途处理
export function pageTransfers(params) {
  return request.get('/item/transfers', { params })
}

export function getTransfer(id) {
  return request.get(`/item/transfers/${stringId(id)}`)
}

export function createTransfer(data) {
  return request.post('/item/transfers', data)
}

export function receiveTransfer(id, data) {
  return request.post(`/item/transfers/${stringId(id)}/receipts`, data)
}

export function requestTransferReturn(id, data) {
  return request.post(`/item/transfers/${stringId(id)}/return_requests`, data)
}

export function receiveTransferReturn(id, data) {
  return request.post(`/item/transfers/${stringId(id)}/return_receipts`, data)
}

export function writeoffTransfer(id, data) {
  return request.post(`/item/transfers/${stringId(id)}/writeoffs`, data)
}

export function pageOverdues(params) {
  return request.get('/item/overdues', { params })
}
