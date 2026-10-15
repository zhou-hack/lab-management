/**
 * 时间工具：业务时区 Asia/Shanghai，传输与存储统一 UTC。
 * - formatInstant：后端 UTC 时间点（ISO 字符串 / Date）→ 上海展示
 * - formatRange：起止时间点 → 上海展示区间
 * - formatShanghaiDate：UTC 时间点 → 上海日期 'YYYY-MM-DD'
 * - toUtcIso：上海墙钟值（'YYYY-MM-DD[ HH:mm[:ss]]'）→ UTC ISO 字符串提交后端
 * - shanghaiLocalToIso：上海日期 + 时间 → 带 +08:00 偏移 ISO 提交后端
 * - shanghaiDayRange：上海自然日 → { startUtc, endUtc }（[当日零点, 次日零点)，UTC ISO）
 * 禁止把无偏移量字符串直接交给 new Date 解析展示；偏移量通过 Intl 显式计算，不手写 +8。
 */

const SHANGHAI = 'Asia/Shanghai'

const pad = (n) => String(n).padStart(2, '0')

const dtf = new Intl.DateTimeFormat('en-CA', {
  timeZone: SHANGHAI,
  hour12: false,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit'
})

/** date 时刻的上海墙钟部件（hour 为 '24' 时归一为 '00'） */
function shanghaiParts(date) {
  const parts = {}
  for (const p of dtf.formatToParts(date)) {
    if (p.type !== 'literal') parts[p.type] = p.value
  }
  if (parts.hour === '24') parts.hour = '00'
  return parts
}

/** date 时刻上海与 UTC 的偏移毫秒数（通过 Intl 往返计算，含毫秒取整） */
function shanghaiOffsetMs(date) {
  const p = shanghaiParts(date)
  const asUtc = Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute, +p.second)
  const floored = Math.floor(date.getTime() / 1000) * 1000
  return asUtc - floored
}

/** 输入（ISO 字符串 / Date）解析为 Date；非法返回 null */
function parseInstant(value) {
  if (!value) return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/**
 * UTC 时间点 → 上海本地显示串，默认 'YYYY-MM-DD HH:mm'。
 * @param {string|Date} value ISO 8601（Z 或带偏移）或 Date
 * @param {{ withDate?: boolean, withSeconds?: boolean }} opts
 */
export function formatInstant(value, opts = {}) {
  const { withDate = true, withSeconds = false } = opts
  const date = parseInstant(value)
  if (!date) return ''
  const p = shanghaiParts(date)
  const time = `${p.hour}:${p.minute}${withSeconds ? `:${p.second}` : ''}`
  return withDate ? `${p.year}-${p.month}-${p.day} ${time}` : time
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

/** UTC 时间点 → 上海日期 'YYYY-MM-DD' */
export function formatShanghaiDate(instant) {
  const text = formatInstant(instant)
  return text ? text.slice(0, 10) : ''
}

/**
 * 上海墙钟值 → UTC ISO 字符串（毫秒）。
 * 接受 'YYYY-MM-DD' 或 'YYYY-MM-DD HH:mm[:ss]'；格式非法抛错（提交前校验）。
 */
export function toUtcIso(local) {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?$/.exec((local || '').trim())
  if (!m) {
    throw new Error(`无效的上海本地时间格式：${local}`)
  }
  const guess = Date.UTC(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0))
  // 二次迭代消除偏移随时刻变化的边界误差
  let ts = guess - shanghaiOffsetMs(new Date(guess))
  ts = guess - shanghaiOffsetMs(new Date(ts))
  return new Date(ts).toISOString()
}

/**
 * 上海本地日期（YYYY-MM-DD）+ 时间（HH:mm）→ 带 +08:00 偏移 ISO 8601 提交后端。
 * 后端 OffsetDateTime 解析后转 UTC 存储；缺参或格式非法返回 null。
 */
export function shanghaiLocalToIso(date, time) {
  if (!date || !time) return null
  const [y, mo, d] = String(date).split('-').map(Number)
  const [h, mi] = String(time).split(':').map(Number)
  if ([y, mo, d, h, mi].some((n) => Number.isNaN(n))) return null
  return `${y}-${pad(mo)}-${pad(d)}T${pad(h)}:${pad(mi)}:00+08:00`
}

/** 上海自然日（'YYYY-MM-DD'）→ { startUtc, endUtc }：[当日零点, 次日零点) 的 UTC ISO。 */
export function shanghaiDayRange(dateStr) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec((dateStr || '').trim())
  if (!m) {
    throw new Error(`无效的日期格式：${dateStr}`)
  }
  const next = new Date(Date.UTC(+m[1], +m[2] - 1, +m[3]) + 86_400_000)
  const nextStr = `${next.getUTCFullYear()}-${pad(next.getUTCMonth() + 1)}-${pad(next.getUTCDate())}`
  return {
    startUtc: toUtcIso(`${m[1]}-${m[2]}-${m[3]} 00:00:00`),
    endUtc: toUtcIso(`${nextStr} 00:00:00`)
  }
}

/** 当前上海本地日期（YYYY-MM-DD） */
export function todayShanghai() {
  const p = shanghaiParts(new Date())
  return `${p.year}-${p.month}-${p.day}`
}

/** 上海本地日期加减天数（YYYY-MM-DD），n 可为负 */
export function addDaysShanghai(dateStr, n) {
  const [y, mo, d] = String(dateStr).split('-').map(Number)
  const ms = Date.UTC(y, mo - 1, d) + n * 86_400_000
  const t = new Date(ms)
  return `${t.getUTCFullYear()}-${pad(t.getUTCMonth() + 1)}-${pad(t.getUTCDate())}`
}

/** 两个上海本地 HH:mm 的分钟差（b - a），跨零点按负值处理（仅当日场景）。 */
export function minutesBetween(timeA, timeB) {
  const toMin = (t) => {
    const [h, m] = String(t).split(':').map(Number)
    return h * 60 + m
  }
  return toMin(timeB) - toMin(timeA)
}
