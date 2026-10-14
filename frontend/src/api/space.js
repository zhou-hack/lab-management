import request from './request'

/* ---------------- 校区 ---------------- */

/** 校区分页查询 */
export function pageCampuses(params) {
  return request.get('/space/campuses', { params })
}

/** 新增校区 */
export function createCampus(data) {
  return request.post('/space/campuses', data)
}

/** 修改校区 */
export function updateCampus(id, data) {
  return request.put(`/space/campuses/${id}`, data)
}

/** 删除校区 */
export function deleteCampus(id) {
  return request.delete(`/space/campuses/${id}`)
}

/* ---------------- 楼栋 ---------------- */

/** 楼栋分页查询 */
export function pageBuildings(params) {
  return request.get('/space/buildings', { params })
}

/** 新增楼栋 */
export function createBuilding(data) {
  return request.post('/space/buildings', data)
}

/** 修改楼栋 */
export function updateBuilding(id, data) {
  return request.put(`/space/buildings/${id}`, data)
}

/** 删除楼栋 */
export function deleteBuilding(id) {
  return request.delete(`/space/buildings/${id}`)
}

/* ---------------- 实验室 ---------------- */

/** 实验室分页查询 */
export function pageLabs(params) {
  return request.get('/space/labs', { params })
}

/** 实验室下拉选项（登录即可，用于借用/台账等场景） */
export function labOptions() {
  return request.get('/space/labs/options')
}

/** 新增实验室 */
export function createLab(data) {
  return request.post('/space/labs', data)
}

/** 修改实验室 */
export function updateLab(id, data) {
  return request.put(`/space/labs/${id}`, data)
}

/** 删除实验室 */
export function deleteLab(id) {
  return request.delete(`/space/labs/${id}`)
}
