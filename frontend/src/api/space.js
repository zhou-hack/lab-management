import request from './request'

/** 场地模块接口封装（前缀 /api/v1/space） */

// 校区/楼栋选项
export function getCampusOptions(onlyEnabled = true) {
  return request.get('/space/campuses/options', { params: { onlyEnabled } })
}

export function getBuildingOptions(campusId, onlyEnabled = true) {
  return request.get('/space/buildings/options', { params: { campusId, onlyEnabled } })
}

// 场地
export function pageLabs(params) {
  return request.get('/space/labs', { params })
}

export function getLabDetail(id) {
  return request.get(`/space/labs/${id}`)
}

export function createLab(data) {
  return request.post('/space/labs', data)
}

export function updateLab(id, data) {
  return request.put(`/space/labs/${id}`, data)
}

export function enableLab(id) {
  return request.post(`/space/labs/${id}/enable`)
}

export function disableLab(id) {
  return request.post(`/space/labs/${id}/disable`)
}

export function deleteLab(id) {
  return request.delete(`/space/labs/${id}`)
}
