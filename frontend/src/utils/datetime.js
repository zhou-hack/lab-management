/**
 * 时间工具：业务时区 Asia/Shanghai，传输/存储统一 UTC。
 * 所有换算显式使用 +08:00 偏移完成，不依赖浏览器本地时区，
 * 禁止把无偏移量字符串直接交给 new Date（规范见 AGENTS.md「时间与 UTC」）。
 */

/** 上海固定偏移（分钟）：中国不实行夏令时，全年 +08:00 */
const SHANGHAI_OFFSET_MIN = 8 * 60

const pad = (n) => String(n).padStart(2, '0')

/** ISO 8601 时间点（带 Z 或偏移量）解析为「上海墙钟」毫秒；非法返回 null */
function toShanghaiMs(value) {
  if (!value) return null
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})?$/.exec(String(value))
  if (!m) return null
  const parsedAsUtc = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6])
  let offsetMin = 0
  if (m[7] && m[7] !== 'Z') {
    const sign = m[7][0] === '-' ? -1 : 1
    offsetMin = sign * (parseInt(m[7].slice(1, 3), 10) * 60 + parseInt(m[7].slice(4, 6), 10))
  }
  // 上海墙钟 = 输入墙钟 + (480 - 输入偏移) 分钟
  return parsedAsUtc + (SHANGHAI_OFFSET_MIN - offsetMin) * 60_000
}

/**
 * UTC 时间点 → 上海本地显示串。
 * @param {string} value ISO 8601（Z 或带偏移）
 * @param {{ withDate?: boolean, withSeconds?: boolean }} opts 默认 'YYYY-MM-DD HH:mm'
 */
export function formatInstant(value, opts = {}) {
  const { withDate = true, withSeconds = false } = opts
  const ms = toShanghaiMs(value)
  if (ms === null) return value ? String(value) : ''
  const d = new Date(ms)
  const date = `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`
  const time = `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}${
    withSeconds ? `:${pad(d.getUTCSeconds())}` : ''
  }`
  return withDate ? `${date} ${time}` : time
}

/** 起止时间点 → 上海本地展示区间（同日省略第二个日期，如「10-14 08:00 ~ 10:00」） */
export function formatRange(startAt, endAt) {
  const s = formatInstant(startAt)
  const e = formatInstant(endAt)
  if (!s || !e) return `${s} ~ ${e}`.trim()
  if (s.slice(0, 10) === e.slice(0, 10)) {
    return `${s} ~ ${e.slice(11)}`
  }
  return `${s} ~ ${e}`
}

/**
 * 上海本地日期（YYYY-MM-DD）+ 时间（HH:mm）→ 带偏移 ISO 8601 提交后端。
 * 固定 +08:00 偏移，后端 OffsetDateTime 解析后转 UTC 存储。
 */
export function shanghaiLocalToIso(date, time) {
  if (!date || !time) return null
  const [y, mo, d] = String(date).split('-').map(Number)
  const [h, mi] = String(time).split(':').map(Number)
  if ([y, mo, d, h, mi].some((n) => Number.isNaN(n))) return null
  return `${y}-${pad(mo)}-${pad(d)}T${pad(h)}:${pad(mi)}:00+08:00`
}

/** 当前上海本地日期（YYYY-MM-DD） */
export function todayShanghai() {
  const d = new Date(Date.now() + SHANGHAI_OFFSET_MIN * 60_000)
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`
}

/** 上海本地日期加减天数（YYYY-MM-DD），n 可为负 */
export function addDaysShanghai(dateStr, n) {
  const [y, mo, d] = String(dateStr).split('-').map(Number)
  const ms = Date.UTC(y, mo - 1, d) + n * 86_400_000
  const t = new Date(ms)
  return `${t.getUTCFullYear()}-${pad(t.getUTCMonth() + 1)}-${pad(t.getUTCDate())}`
}

/**
 * 两个上海本地 HH:mm 的分钟差（b - a），跨零点按负值处理（仅当日场景）。
 */
export function minutesBetween(timeA, timeB) {
  const toMin = (t) => {
    const [h, m] = String(t).split(':').map(Number)
    return h * 60 + m
  }
  return toMin(timeB) - toMin(timeA)
}
