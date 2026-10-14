/**
 * 时间工具：业务时区 Asia/Shanghai，传输与存储统一 UTC。
 * - 出参 ISO 字符串显式按上海时区展示，不依赖浏览器本地时区
 * - 禁止把无偏移量字符串直接交给 new Date（仓库约定）
 */
const SH_TZ = 'Asia/Shanghai'

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
