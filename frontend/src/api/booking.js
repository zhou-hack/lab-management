import request from './request'

/**
 * 场地预约 API（BookingController，前缀 /api/v1/booking/labs）。
 * 时间点入参均为带偏移 ISO 8601（如 2026-12-01T08:00:00+08:00），
 * 出参为 UTC Instant，展示时经 utils/datetime.js 按上海时区格式化。
 */

/** 发起预约（booking:lab:create） */
export function createBooking(data) {
  return request.post('/booking/labs', data)
}

/** 我的预约分页（booking:lab:list） */
export function pageMyBookings(params) {
  return request.get('/booking/labs', { params })
}

/** 全部预约分页（booking:lab:approve） */
export function pageAllBookings(params) {
  return request.get('/booking/labs/all', { params })
}

/** 预约情况只读分页（booking:lab:view，学生只读视角） */
export function pageBoardBookings(params) {
  return request.get('/booking/labs/board', { params })
}

/** 开门签到（booking:lab:approve，已批准且到点） */
export function checkinBooking(id) {
  return request.post(`/booking/labs/${id}/checkin`)
}

/** 结束清点（booking:lab:approve，已签到后；note 选填） */
export function checkoutBooking(id, note) {
  return request.post(`/booking/labs/${id}/checkout`, { note })
}

/** 预约统计（按状态计数，booking:lab:approve） */
export function getBookingStats() {
  return request.get('/booking/labs/stats')
}

/** 场地某日可用时段（booking:lab:create），date 为上海 YYYY-MM-DD */
export function getAvailability(spaceId, date) {
  return request.get('/booking/labs/availability', { params: { spaceId, date } })
}

/**
 * 场地占用总览（任意预约权限可读）：按日期 + 可选起止节次返回全部场地
 * 空闲/占用分档，占用附 teacherName/applicantName/courseName 与占用时段。
 */
export function getLabOccupancy(params) {
  return request.get('/booking/labs/occupancy', { params })
}

/** 预约详情（本人或审批权限） */
export function getBookingDetail(id) {
  return request.get(`/booking/labs/${id}`)
}

/** 取消预约（booking:lab:cancel，仅申请人本人） */
export function cancelBooking(id, reason) {
  return request.post(`/booking/labs/${id}/cancel`, { reason })
}

/** 管理员强制取消（booking:lab:approve，仅已批准单；原因必填，事件记 FORCE_CANCEL） */
export function forceCancelBooking(id, reason) {
  return request.post(`/booking/labs/${id}/force_cancel`, { reason })
}

/** 批准预约（booking:lab:approve，占用时段生效） */
export function approveBooking(id) {
  return request.post(`/booking/labs/${id}/approve`)
}

/**
 * 批量批准（booking:lab:approve，≤100 条，逐单独立互不影响）。
 * resolve BatchApproveResultVO：{ approvedIds:[], failedItems:[{id,msg}] }
 */
export function batchApproveBookings(ids) {
  return request.post('/booking/labs/batch_approve', { ids })
}

/** 预约记录 CSV 导出（与全部预约同条件，UTF-8 BOM，上限 10000 条），返回 Blob */
export function exportAllBookings(params) {
  return request.get('/booking/labs/all/export', { params, responseType: 'blob' })
}

/** 驳回预约（booking:lab:approve，原因必填） */
export function rejectBooking(id, reason) {
  return request.post(`/booking/labs/${id}/reject`, { reason })
}

// ---------------------------------------------------------------------------
// 节次时间轴 + 批量周期预约（BookingSeriesController，前缀 /api/v1/booking）
// ---------------------------------------------------------------------------

/** 已启用节次列表（时间轴节次小卡片；任意预约权限可读） */
export function listPeriods() {
  return request.get('/booking/periods')
}

/** 上课老师选项（预约表单「老师」下拉；任意预约权限可读） */
export function listTeachers() {
  return request.get('/booking/teachers')
}

/** 场地近期预约（24h 窗口，时间轴「近期预约」卡片；任意预约权限可读） */
export function listRecentBookings(spaceId) {
  return request.get('/booking/labs/recent', { params: { spaceId } })
}

/** 提交批量周期预约（老师；幂等键防重复提交） */
export function createSeries(data) {
  return request.post('/booking/series', data)
}

/** 我的周期预约分页（booking:lab:list） */
export function pageMySeries(params) {
  return request.get('/booking/series', { params })
}

/** 全部周期预约分页（booking:lab:approve） */
export function pageAllSeries(params) {
  return request.get('/booking/series/all', { params })
}

/** 周期预约详情（本人或审批/只读权限，附生成的各周预约单） */
export function getSeriesDetail(id) {
  return request.get(`/booking/series/${id}`)
}

/** 管理员批准周期预约（逐周生成占用） */
export function approveSeries(id) {
  return request.post(`/booking/series/${id}/approve`)
}

/** 管理员驳回周期预约（原因必填） */
export function rejectSeries(id, reason) {
  return request.post(`/booking/series/${id}/reject`, { reason })
}

/** 申请人本人撤销周期预约 */
export function cancelSeries(id, reason) {
  return request.post(`/booking/series/${id}/cancel`, { reason })
}
