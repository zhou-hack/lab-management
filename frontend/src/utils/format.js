/**
 * 通用格式化工具（物料模块骨架页使用）
 */

/** UTC ISO 时间串 → 上海时区 'YYYY-MM-DD HH:mm:ss' 展示；空值返回空串 */
export function fmtShanghai(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  // sv-SE 区域恰好输出连字符日期格式；hourCycle h23 避免午夜显示为 24:00
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23'
  }).format(d)
}

/** 当前 UTC 时间（带 Z 的 ISO 8601），作为业务发生时间的默认值 */
export function nowUtcIso() {
  return new Date().toISOString()
}

/** 今天的上海日期 'YYYY-MM-DD'（纯日期，不做时区转换） */
export function todayShanghai() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
}

/**
 * 上海本地时间 'YYYY-MM-DD HH:mm:ss' → 带 Z 的 UTC ISO 8601
 * 显式按 +08:00 偏移解析，不依赖浏览器默认时区；空值返回空串
 */
export function shanghaiToUtcIso(localDateTime) {
  if (!localDateTime) return ''
  return new Date(`${String(localDateTime).replace(' ', 'T')}+08:00`).toISOString()
}

/** 复制文本到剪贴板，成功提示（复制行 ID 供归还/核销引用） */
export async function copyText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(String(text))
    const { ElMessage } = await import('element-plus')
    ElMessage.success('已复制')
  } catch {
    const { ElMessage } = await import('element-plus')
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

/**
 * 数量/金额展示：去掉无意义的小数尾零
 * 后端 DECIMAL 返回字符串（如 '100.000'、'20.0000'），展示为 '100'、'20'
 * 非数字内容原样返回；空值返回空串
 */
export function formatQty(value) {
  if (value === null || value === undefined || value === '') return ''
  const text = String(value)
  if (!/^-?\d+(\.\d+)?$/.test(text)) return text
  if (!text.includes('.')) return text
  const trimmed = text.replace(/0+$/, '').replace(/\.$/, '')
  return trimmed === '-0' ? '0' : trimmed
}
