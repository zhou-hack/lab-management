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

/** 台账分页查询（无维护权限时服务端收敛为在架可约目录视图） */
export function pageAssets(params) {
  return request.get('/equipment/assets', { params })
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

/* ---------------- 器材借用 ---------------- */

/** 借用单分页查询（scope=MINE/PENDING/ALL） */
export function pageBookings(params) {
  return request.get('/equipment/bookings', { params })
}

/** 借用单详情 */
export function bookingDetail(id) {
  return request.get(`/equipment/bookings/${id}`)
}

/** 审批老师下拉选项（学生提交借用时选择） */
export function teacherOptions() {
  return request.get('/equipment/bookings/teachers')
}

/** 提交借用单（服务端校验时间/限制/冲突；幂等键命中返回原单） */
export function createBooking(data) {
  return request.post('/equipment/bookings', data)
}

/** 撤销借用单（仅 PENDING 态本人） */
export function cancelBooking(id, data) {
  return request.post(`/equipment/bookings/${id}/cancel`, data)
}

/** 老师审批（approved=false 时意见必填） */
export function teacherApproveBooking(id, data) {
  return request.post(`/equipment/bookings/${id}/teacher_approve`, data)
}

/** 管理员审批（approved=false 时意见必填） */
export function adminApproveBooking(id, data) {
  return request.post(`/equipment/bookings/${id}/admin_approve`, data)
}

/** 借出确认（须在借用时间窗口内） */
export function checkoutBooking(id, data) {
  return request.post(`/equipment/bookings/${id}/checkout`, data)
}

/** 归还确认（condition=DAMAGED 时 note 必填） */
export function returnBooking(id, data) {
  return request.post(`/equipment/bookings/${id}/return`, data)
}
