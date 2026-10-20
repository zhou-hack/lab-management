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

/**
 * 周期预约状态字典（SeriesStatus）与胶囊映射。
 */
export const SERIES_STATUS = [
  { value: 'PENDING', label: '待审批', pill: 'pill--warn' },
  { value: 'APPROVED', label: '已批准', pill: 'pill--ok' },
  { value: 'REJECTED', label: '已驳回', pill: 'pill--danger' },
  { value: 'CANCELLED', label: '已取消', pill: 'pill--muted' }
]

export const SERIES_STATUS_MAP = SERIES_STATUS.reduce((map, item) => {
  map[item.value] = item
  return map
}, {})

/**
 * 节次卡片三色派生（前端按当前时间计算，不落库）：
 *  - free       绿：空闲，无排课无预约，可点选
 *  - occupied   红：已占用（已批准预约/排课占用）
 *  - pending    黄：下节课要使用，之后紧邻有排课/预约、即将占用，谨慎选择
 *  - past       灰：已过去的节次（不可选）
 */
export const PERIOD_CARD_MAP = {
  free: { cls: 'period_card--free', tip: '空闲 · 可点选' },
  occupied: { cls: 'period_card--occupied', tip: '已占用 · 不可选' },
  pending: { cls: 'period_card--pending', tip: '下节课即将使用 · 谨慎选择' },
  past: { cls: 'period_card--past', tip: '已过去 · 不可选' }
}
