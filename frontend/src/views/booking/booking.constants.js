/**
 * 预约模块前端字典（与后端 BookingStatus/BookingVO 契约一致）。
 * runtimePhase 为 APPROVED 派生展示态：UPCOMING 未开始 / IN_USE 使用中 / FINISHED 已结束。
 */

export const BOOKING_STATUS = [
  { value: 'PENDING', label: '待审批', pill: 'pill--warn' },
  { value: 'APPROVED', label: '已批准', pill: 'pill--ok' },
  { value: 'REJECTED', label: '已驳回', pill: 'pill--danger' },
  { value: 'CANCELLED', label: '已取消', pill: 'pill--muted' }
]

export const BOOKING_STATUS_MAP = BOOKING_STATUS.reduce((map, item) => {
  map[item.value] = item
  return map
}, {})

export const RUNTIME_PHASE_MAP = {
  UPCOMING: { label: '未开始', pill: 'pill--brand' },
  IN_USE: { label: '使用中', pill: 'pill--ok' },
  FINISHED: { label: '已结束', pill: 'pill--muted' }
}
