import { describe, expect, it } from 'vitest'
import { formatInstant, formatShanghaiDate, shanghaiDayRange, toUtcIso } from './datetime'

describe('datetime 上海时区工具', () => {
  it('UTC 时间点按上海时区展示', () => {
    expect(formatInstant('2026-10-13T16:00:00.000Z')).toBe('2026-10-14 00:00:00')
    expect(formatInstant('2026-10-13T01:00:00.000Z')).toBe('2026-10-13 09:00:00')
  })

  it('formatInstant 兼容 Date 与无效输入', () => {
    expect(formatInstant(new Date('2026-10-13T16:00:00.000Z'))).toBe('2026-10-14 00:00:00')
    expect(formatInstant('')).toBe('')
    expect(formatInstant('not-a-date')).toBe('')
  })

  it('formatShanghaiDate 截取上海日期', () => {
    expect(formatShanghaiDate('2026-10-13T16:00:00.000Z')).toBe('2026-10-14')
  })

  it('上海墙钟值转 UTC ISO（含跨日边界）', () => {
    expect(toUtcIso('2026-10-14 00:00:00')).toBe('2026-10-13T16:00:00.000Z')
    expect(toUtcIso('2026-10-13 09:00')).toBe('2026-10-13T01:00:00.000Z')
    expect(toUtcIso('2026-10-13')).toBe('2026-10-12T16:00:00.000Z')
  })

  it('toUtcIso 拒绝非法格式', () => {
    expect(() => toUtcIso('2026/10/14 00:00:00')).toThrow()
    expect(() => toUtcIso('')).toThrow()
  })

  it('上海自然日范围 [当日零点, 次日零点)', () => {
    expect(shanghaiDayRange('2026-10-14')).toEqual({
      startUtc: '2026-10-13T16:00:00.000Z',
      endUtc: '2026-10-14T16:00:00.000Z'
    })
    // 跨月边界
    expect(shanghaiDayRange('2026-10-31').endUtc).toBe('2026-10-31T16:00:00.000Z')
  })

  it('UTC↔上海往返一致', () => {
    const utc = toUtcIso('2026-03-01 08:30:00')
    expect(formatInstant(utc)).toBe('2026-03-01 08:30:00')
  })
})
