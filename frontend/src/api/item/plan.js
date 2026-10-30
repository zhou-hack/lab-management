import request from '../request'

export function listMaterialPlans(enabled) {
  return request.get('/item/plans', {
    params: { enabled: enabled === '' || enabled === null || enabled === undefined ? undefined : enabled }
  })
}

export function getMaterialPlan(id) {
  return request.get(`/item/plans/${id}`)
}

export function getPlanMaterials(params) {
  return request.get('/item/plans/materials', { params })
}

export function createMaterialPlan(data) {
  return request.post('/item/plans', data)
}

export function updateMaterialPlan(id, data) {
  return request.put(`/item/plans/${id}`, data)
}
