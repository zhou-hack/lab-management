import request from '@/api/request'

export function getMaterials(params) {
  return request.get('/item/materials', { params })
}

export function getMaterialOptions(params) {
  return request.get('/item/materials/options', { params })
}

export function getMaterial(id) {
  return request.get(`/item/materials/${id}`)
}

export function createMaterial(data) {
  return request.post('/item/materials', data)
}

export function updateMaterial(id, data) {
  return request.put(`/item/materials/${id}`, data)
}

export function deleteMaterial(id) {
  return request.delete(`/item/materials/${id}`)
}

export function getAssets(params) {
  return request.get('/item/assets', { params })
}

export function getAssetSummary() {
  return request.get('/item/assets/summary')
}

export function getAsset(id) {
  return request.get(`/item/assets/${id}`)
}

export function updateAsset(id, data) {
  return request.put(`/item/assets/${id}`, data)
}

export function toggleAssetMaintenance(id) {
  return request.post(`/item/assets/${id}/maintenance`)
}

export function getLocations(params) {
  return request.get('/item/locations', { params })
}

export function getLocationAdminOptions(params) {
  return request.get('/item/locations/admin_options', { params })
}

export function getLocation(id) {
  return request.get(`/item/locations/${id}`)
}

export function getLocationInventory(id) {
  return request.get(`/item/locations/${id}/inventory`)
}

export function createLocation(data) {
  return request.post('/item/locations', data)
}

export function updateLocation(id, data) {
  return request.put(`/item/locations/${id}`, data)
}

export function updateLocationAdmins(id, userIds) {
  return request.put(`/item/locations/${id}/admins`, { userIds })
}

export function getResponsibleHistory(params) {
  return request.get('/item/locations/responsible_history', { params })
}

export function getLocationChangeHistory(params) {
  return request.get('/item/locations/change_history', { params })
}

export function deleteLocation(id) {
  return request.delete(`/item/locations/${id}`)
}

export function getBalances(params) {
  return request.get('/item/balances', { params })
}

export function updateBalancePosition(id, data) {
  return request.put(`/item/balances/${id}/position`, data)
}

export function getMovements(params) {
  return request.get('/item/movements', { params })
}

export function createOpening(data) {
  return request.post('/item/documents/opening', data)
}

export function createReplenishment(data) {
  return request.post('/item/documents/replenishment', data)
}

function uploadAssetFile(path, file, locationId, section, timeout = 60000) {
  const data = new FormData()
  data.append('file', file)
  data.append('locationId', locationId)
  data.append('section', String(section))
  return request.post(path, data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout
  })
}

export function previewAssetImport(file, locationId, section = 1) {
  return uploadAssetFile('/item/assets/import/preview', file, locationId, section)
}

export function importAssets(file, locationId, section = 1) {
  return uploadAssetFile('/item/assets/import', file, locationId, section, 180000)
}
