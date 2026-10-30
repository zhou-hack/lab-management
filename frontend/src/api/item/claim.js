import request from '../request'

/** 当前用户的申领分页（申请人范围由后端强制限定）。 */
export function pageMyClaims(params) {
  return request.get('/item/claims', { params })
}

export function getClaimDetail(id) {
  return request.get(`/item/claims/${id}`)
}

export function getClaimAvailability(params) {
  return request.get('/item/claims/availability', { params })
}

export function previewClaim(data) {
  return request.post('/item/claims/preview', data)
}

export function createClaim(data) {
  return request.post('/item/claims', data)
}

export function updateClaim(id, data) {
  return request.put(`/item/claims/${id}`, data)
}

export function updateClaimDelegation(id, studentId) {
  return request.put(`/item/claims/${String(id)}/delegation`, {
    studentId: studentId === null ? null : String(studentId)
  })
}

export function cancelClaim(id, reason) {
  return request.post(`/item/claims/${id}/cancel`, { reason: reason || undefined })
}

export function abandonClaim(id, reason) {
  return request.post(`/item/claims/${id}/abandon`, { reason: reason || undefined })
}

export function getClaimDocuments(id) {
  return request.get(`/item/claims/${id}/documents`)
}

/** 再次预约模板，仅包含原单用途和当前版本物料数量。 */
export function getClaimTemplate(id) {
  return request.get(`/item/claims/${id}/template`)
}

/** 教师端不传 applicantId，后端会强制限定为本人逾期记录。 */
export function pageMyOverdues(params) {
  return request.get('/item/overdues', { params })
}
