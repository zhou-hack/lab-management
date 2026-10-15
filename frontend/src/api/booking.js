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

/** 预约统计（按状态计数，booking:lab:approve） */
export function getBookingStats() {
  return request.get('/booking/labs/stats')
}

/** 场地某日可用时段（booking:lab:create），date 为上海本地 YYYY-MM-DD */
export function getAvailability(spaceId, date) {
  return request.get('/booking/labs/availability', { params: { spaceId, date } })
}

/** 预约详情（本人或审批权限） */
export function getBookingDetail(id) {
  return request.get(`/booking/labs/${id}`)
}

/** 取消预约（booking:lab:cancel，仅申请人本人） */
export function cancelBooking(id, reason) {
  return request.post(`/booking/labs/${id}/cancel`, { reason })
}

/** 批准预约（booking:lab:approve，占用时段生效） */
export function approveBooking(id) {
  return request.post(`/booking/labs/${id}/approve`)
}

/** 驳回预约（booking:lab:approve，原因必填） */
export function rejectBooking(id, reason) {
  return request.post(`/booking/labs/${id}/reject`, { reason })
}
