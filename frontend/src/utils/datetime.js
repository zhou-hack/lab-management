/**
 * 时间工具：业务时区 Asia/Shanghai，传输与存储统一 UTC。
 * - formatInstant：后端 UTC 时间点（ISO 字符串 / Date）→ 上海展示 'YYYY-MM-DD HH:mm:ss'
 * - toUtcIso：选择器上海墙钟值（'YYYY-MM-DD HH:mm:ss'）→ UTC ISO 字符串提交后端
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

/** UTC 时间点 → 上海展示 'YYYY-MM-DD HH:mm:ss'；无效输入返回空串 */
export function formatInstant(instant) {
  if (!instant) return ''
  const date = instant instanceof Date ? instant : new Date(instant)
  if (Number.isNaN(date.getTime())) return ''
  const p = shanghaiParts(date)
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`
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
 * 上海自然日（'YYYY-MM-DD'）→ { startUtc, endUtc }：[当日零点, 次日零点) 的 UTC ISO。
 * 后端按此做 >= startUtc AND < endUtc 查询。
 */
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
