import { describe, it, expect } from 'vitest'
import {
  formatInstant,
  formatRange,
  shanghaiLocalToIso,
  todayShanghai,
  addDaysShanghai,
  minutesBetween
} from './datetime'

describe('formatInstant UTC→上海展示', () => {
  it('Z 后缀 UTC 时间点 +8 小时展示', () => {
    expect(formatInstant('2026-10-14T01:00:00.000Z')).toBe('2026-10-14 09:00')
  })

  it('+08:00 偏移输入不重复换算', () => {
    expect(formatInstant('2026-10-14T09:00:00+08:00')).toBe('2026-10-14 09:00')
  })

  it('-05:00 偏移输入换算 13 小时（跨上海日界）', () => {
    expect(formatInstant('2026-10-14T15:00:00-05:00')).toBe('2026-10-15 04:00')
  })

  it('仅时间模式与秒模式', () => {
    expect(formatInstant('2026-10-14T01:02:03Z', { withDate: false })).toBe('09:02')
    expect(formatInstant('2026-10-14T01:02:03Z', { withSeconds: true })).toBe('2026-10-14 09:02:03')
  })

  it('空值与非法输入原样返回', () => {
    expect(formatInstant('')).toBe('')
    expect(formatInstant('abc')).toBe('abc')
  })
})

describe('formatRange 区间展示', () => {
  it('同日省略第二个日期', () => {
    expect(formatRange('2026-10-14T01:00:00Z', '2026-10-14T03:00:00Z')).toBe('2026-10-14 09:00 ~ 11:00')
  })

  it('跨日保留完整日期', () => {
    expect(formatRange('2026-10-14T10:00:00Z', '2026-10-15T02:00:00Z')).toBe('2026-10-14 18:00 ~ 2026-10-15 10:00')
  })
})

describe('shanghaiLocalToIso 上海本地→偏移 ISO', () => {
  it('生成固定 +08:00 偏移', () => {
    expect(shanghaiLocalToIso('2026-12-01', '08:30')).toBe('2026-12-01T08:30:00+08:00')
  })

  it('缺失日期或时间返回 null', () => {
    expect(shanghaiLocalToIso('', '08:00')).toBeNull()
    expect(shanghaiLocalToIso('2026-12-01', '')).toBeNull()
  })
})

describe('上海日期工具', () => {
  it('todayShanghai 返回 YYYY-MM-DD', () => {
    expect(todayShanghai()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('addDaysShanghai 正负天数', () => {
    expect(addDaysShanghai('2026-10-31', 1)).toBe('2026-11-01')
    expect(addDaysShanghai('2026-03-01', -1)).toBe('2026-02-28')
  })

  it('minutesBetween 计算分钟差', () => {
    expect(minutesBetween('08:00', '10:30')).toBe(150)
    expect(minutesBetween('10:30', '08:00')).toBe(-150)
  })
})
