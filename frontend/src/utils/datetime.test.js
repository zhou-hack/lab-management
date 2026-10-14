import { describe, expect, it } from 'vitest'
import { formatSh } from './datetime'

describe('formatSh', () => {
  it('UTC 出参按上海时区展示（三位毫秒 ISO → 墙钟）', () => {
    expect(formatSh('2026-10-13T01:00:00.000Z')).toBe('2026-10-13 09:00')
  })

  it('带秒展示', () => {
    expect(formatSh('2026-10-13T01:00:05.123Z', true)).toBe('2026-10-13 09:00:05')
  })

  it('UTC 16:00 显示为上海次日 00:00（跨日进位）', () => {
    expect(formatSh('2026-10-13T16:00:00.000Z')).toBe('2026-10-14 00:00')
  })

  it('空值显示占位符，无偏移量字符串原样返回', () => {
    expect(formatSh('')).toBe('—')
    expect(formatSh(null)).toBe('—')
    expect(formatSh('2026-10-13 09:00')).toBe('2026-10-13 09:00')
  })

  it('带 +08:00 数字偏移量同样可解析', () => {
    expect(formatSh('2026-10-13T09:00:00+08:00')).toBe('2026-10-13 09:00')
  })
})
