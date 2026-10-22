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

/** 台账导出（xlsx，按当前筛选全量导出，返回 Blob） */
export function exportAssets(params) {
  return request.get('/equipment/assets/export', { params, responseType: 'blob' })
}

/** 台账导入（xlsx，导出文件即模板） */
export function importAssets(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/equipment/assets/import', formData, { timeout: 60000 })
}

/* ---------------- 设备使用申请（三权级两级审批） ---------------- */

/** 提交使用申请（学生须指定审批教师；教师普通设备免初审） */
export function applyBooking(data) {
  return request.post('/equipment/bookings', data)
}

/** 本人申请分页 */
export function pageMyBookings(params) {
  return request.get('/equipment/bookings/my', { params })
}

/** 撤销本人申请（仅借出前） */
export function cancelBooking(id, data) {
  return request.put(`/equipment/bookings/${id}/cancel`, data || {})
}

/** 教师待初审分页 */
export function pageTeacherPending(params) {
  return request.get('/equipment/bookings/teacher_pending', { params })
}

/** 教师初审（approve 通过即生效；reject 意见必填） */
export function teacherReview(id, data) {
  return request.put(`/equipment/bookings/${id}/teacher_review`, data)
}

/** 全部申请分页（管理员） */
export function pageAllBookings(params) {
  return request.get('/equipment/bookings', { params })
}

/** 管理员终审/代批 */
export function adminReview(id, data) {
  return request.put(`/equipment/bookings/${id}/admin_review`, data)
}

/** 借出发放登记（设备置借出中） */
export function checkoutBooking(id) {
  return request.put(`/equipment/bookings/${id}/checkout`)
}

/** 归还登记（完好恢复空闲；损坏备注必填转维护中） */
export function returnBooking(id, data) {
  return request.put(`/equipment/bookings/${id}/return`, data)
}

/** 审批教师下拉（TEACHER 角色启用用户） */
export function teacherOptions() {
  return request.get('/equipment/bookings/teacher_options')
}
