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

/**
 * 节次×场地矩阵单元格状态派生（日矩阵渲染与父组件区间点选校验共用同一口径）：
 * 返回 Map<`${spaceId}:${periodNo}`, { state, text }>。
 *  - state: off 场地非 AVAILABLE；past 已过去；occupied 与未释放占用段半开区间重叠；
 *           pending 下一节次被占用；free 空闲可点选
 *  - text:  occupied 时的占用人/课程展示文本
 */
import { shanghaiClockOf } from '@/utils/datetime'

const toMin = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5))

export function buildPeriodCellMap(labs, periods, date, today, nowClock) {
  const map = new Map()
  const nowMin = toMin(nowClock)
  for (const lab of labs || []) {
    const slots = (lab.slots || []).map((s) => ({
      start: toMin(shanghaiClockOf(s.startAt) || '00:00'),
      end: toMin(shanghaiClockOf(s.endAt) || '00:00'),
      text: s.courseName || s.teacherName || s.applicantName || '已占用'
    }))
    for (let idx = 0; idx < (periods || []).length; idx++) {
      const p = periods[idx]
      const sMin = toMin(p.startClock)
      const eMin = toMin(p.endClock)
      let state
      let text = ''
      if (lab.status !== 'AVAILABLE') {
        state = 'off'
      } else if (date < today || (date === today && eMin <= nowMin)) {
        state = 'past'
      } else {
        const hit = slots.find((seg) => seg.end > sMin && seg.start < eMin)
        if (hit) {
          state = 'occupied'
          text = hit.text
        } else {
          const next = periods[idx + 1]
          const nextHit = next
            && slots.some((seg) => seg.end > toMin(next.startClock) && seg.start < toMin(next.endClock))
          state = nextHit ? 'pending' : 'free'
        }
      }
      map.set(`${lab.spaceId}:${p.periodNo}`, { state, text })
    }
  }
  return map
}

/**
 * 场地当前状态四态派生（矩阵行头徽章与原占用总览卡片共用口径）：
 *  - off       维护中/停用（不参与占用判断）
 *  - occupied  当前时刻落在某未释放占用段内
 *  - reserved  下一占用段 60 分钟内开始（下节课预留，不可新预约）
 *  - released  今日占用段已全部结束（资源已释放，可预约）
 *  - free      空闲
 * 非今天日期仅区分 occupied/free（有/无当日占用段）。
 */
export function labStateOf(lab, date, today, nowMs) {
  if (lab.status !== 'AVAILABLE') return 'off'
  const slots = lab.slots || []
  if (!slots.length) return 'free'
  if (date !== today) return 'occupied'
  const toMs = (iso) => new Date(iso).getTime()
  if (slots.some((s) => toMs(s.startAt) <= nowMs && nowMs < toMs(s.endAt))) return 'occupied'
  const next = slots.find((s) => toMs(s.startAt) > nowMs)
  if (!next) return 'released'
  return toMs(next.startAt) - nowMs <= 60 * 60 * 1000 ? 'reserved' : 'free'
}

/** 场地当前状态徽章文案与样式（pill 类） */
export const LAB_STATE_MAP = {
  free: { label: '空闲', pill: 'pill--ok' },
  occupied: { label: '占用中', pill: 'pill--danger' },
  reserved: { label: '预留', pill: 'pill--warn' },
  released: { label: '已释放', pill: 'pill--muted' },
  off: { label: '不可用', pill: 'pill--muted' }
}
