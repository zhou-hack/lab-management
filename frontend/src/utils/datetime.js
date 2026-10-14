/**
 * 时间工具：业务时区 Asia/Shanghai，传输与存储统一 UTC。
 * - 选择器值按上海墙钟解析后提交带 Z 偏移的 ISO 8601（三位毫秒）
 * - 出参 ISO 字符串显式按上海时区展示，不依赖浏览器本地时区
 * - 禁止把无偏移量字符串直接交给 new Date（仓库约定）
 */
const SH_TZ = 'Asia/Shanghai'

/**
 * 计算某时刻在指定时区的偏移毫秒数（通过 Intl formatToParts，不依赖系统时区）
 */
function zoneOffsetMs(date, timeZone) {
  const dtf = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
  const parts = {}
  for (const part of dtf.formatToParts(date)) {
    parts[part.type] = part.value
  }
  const hour = parts.hour === '24' ? 0 : Number(parts.hour)
  const wallAsUtc = Date.UTC(
    Number(parts.year),
    Number(parts.month) - 1,
    Number(parts.day),
    hour,
    Number(parts.minute),
    Number(parts.second)
  )
  return wallAsUtc - date.getTime()
}

/**
 * 上海墙钟字符串（yyyy-MM-dd HH:mm，选择器 value-format）→ 带 Z 偏移的 UTC ISO
 * 例：'2026-10-14 10:00' → '2026-10-14T02:00:00.000Z'
 */
export function shWallClockToUtcIso(text) {
  const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})$/.exec((text || '').trim())
  if (!match) {
    throw new Error(`无效的上海墙钟时间：${text}`)
  }
  const wallAsUtc = Date.UTC(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    Number(match[4]),
    Number(match[5])
  )
  const offset = zoneOffsetMs(new Date(wallAsUtc), SH_TZ)
  return new Date(wallAsUtc - offset).toISOString()
}

/**
 * 带偏移量的 ISO 字符串（后端出参，如 2026-10-13T01:00:00.000Z）→ 上海墙钟显示
 * 例：'2026-10-13T01:00:00.000Z' → '2026-10-13 09:00'
 */
export function formatSh(isoText, withSeconds = false) {
  if (!isoText) {
    return '—'
  }
  // 仅接受带 Z 或数字偏移量的 ISO 字符串，禁止无偏移量字符串
  if (!/^\d{4}-\d{2}-\d{2}T.*(Z|[+-]\d{2}:\d{2})$/.test(isoText)) {
    return isoText
  }
  const date = new Date(isoText)
  if (Number.isNaN(date.getTime())) {
    return isoText
  }
  const options = {
    timeZone: SH_TZ,
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }
  if (withSeconds) {
    options.second = '2-digit'
  }
  const parts = {}
  for (const part of new Intl.DateTimeFormat('en-CA', options).formatToParts(date)) {
    parts[part.type] = part.value
  }
  const hour = parts.hour === '24' ? '00' : parts.hour
  let text = `${parts.year}-${parts.month}-${parts.day} ${hour}:${parts.minute}`
  if (withSeconds) {
    text += `:${parts.second}`
  }
  return text
}
