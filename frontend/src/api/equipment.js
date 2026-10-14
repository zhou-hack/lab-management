import request from './request'

/* ---------------- 设备分类 ---------------- */

/** 分类树查询（全量，含停用节点） */
export function categoryTree() {
  return request.get('/equipment/categories')
}

/** 新增分类 */
export function createCategory(data) {
  return request.post('/equipment/categories', data)
}

/** 修改分类（parent/name/enabled） */
export function updateCategory(id, data) {
  return request.put(`/equipment/categories/${id}`, data)
}

/** 删除分类 */
export function deleteCategory(id) {
  return request.delete(`/equipment/categories/${id}`)
}

/* ---------------- 设备型号 ---------------- */

/** 型号分页查询 */
export function pageModels(params) {
  return request.get('/equipment/models', { params })
}

/** 新增型号 */
export function createModel(data) {
  return request.post('/equipment/models', data)
}

/** 修改型号（型号码不可改） */
export function updateModel(id, data) {
  return request.put(`/equipment/models/${id}`, data)
}

/** 删除型号 */
export function deleteModel(id) {
  return request.delete(`/equipment/models/${id}`)
}

/* ---------------- 设备台账 ---------------- */

/** 台账分页查询（无维护权限时服务端收敛为在架目录视图） */
export function pageAssets(params) {
  return request.get('/equipment/assets', { params })
}

/** 保管人下拉选项（全部启用用户） */
export function custodianOptions() {
  return request.get('/equipment/assets/custodian-options')
}

/** 新增台账 */
export function createAsset(data) {
  return request.post('/equipment/assets', data)
}

/** 修改台账（存放位置/保管人/购置信息/启用状态） */
export function updateAsset(id, data) {
  return request.put(`/equipment/assets/${id}`, data)
}

/** 删除台账 */
export function deleteAsset(id) {
  return request.delete(`/equipment/assets/${id}`)
}

/** 迁移登记 */
export function transferAsset(id, data) {
  return request.put(`/equipment/assets/${id}/transfer`, data)
}

/** 价值定级 */
export function classifyAsset(id, data) {
  return request.put(`/equipment/assets/${id}/classification`, data)
}
