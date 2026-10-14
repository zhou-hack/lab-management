import request from './request'

/** 场地模块接口封装（前缀 /api/v1/space/spaces，契约见 docs/场地模块开发文档清单.md 3.2） */

export function pageSpaces(params) {
  return request.get('/space/spaces', { params })
}

/** 统计卡片：总块数与各状态块数 */
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

export function deleteSpace(id) {
  return request.delete(`/space/spaces/${id}`)
}
