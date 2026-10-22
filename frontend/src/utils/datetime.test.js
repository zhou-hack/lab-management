import { describe, expect, it } from 'vitest'
import {
  addDaysShanghai,
  formatInstant,
  formatRange,
  formatShanghaiDate,
  minutesBetween,
  shanghaiDayRange,
  shanghaiLocalToIso,
  todayShanghai,
  toShOffsetIso,
  toUtcIso
} from './datetime'

describe('formatInstant UTC→上海展示', () => {
  it('Z 后缀 UTC 时间点 +8 小时展示', () => {
    expect(formatInstant('2026-10-14T01:00:00.000Z')).toBe('2026-10-14 09:00')
    expect(formatInstant('2026-10-13T16:00:00.000Z')).toBe('2026-10-14 00:00')
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

  it('兼容 Date 实例', () => {
    expect(formatInstant(new Date('2026-10-13T16:00:00.000Z'))).toBe('2026-10-14 00:00')
  })

  it('空值与非法输入返回空串', () => {
    expect(formatInstant('')).toBe('')
    expect(formatInstant(null)).toBe('')
    expect(formatInstant('not-a-date')).toBe('')
  })
})

describe('formatRange 区间展示', () => {
  it('同日省略第二个日期', () => {
    expect(formatRange('2026-10-14T01:00:00Z', '2026-10-14T03:00:00Z')).toBe('2026-10-14 09:00 ~ 11:00')
  })

  it('跨日保留完整日期', () => {
    expect(formatRange('2026-10-14T10:00:00Z', '2026-10-15T02:00:00Z')).toBe(
      '2026-10-14 18:00 ~ 2026-10-15 10:00'
    )
  })
})

describe('formatShanghaiDate', () => {
  it('截取上海日期', () => {
    expect(formatShanghaiDate('2026-10-13T16:00:00.000Z')).toBe('2026-10-14')
  })
})

describe('toUtcIso 上海墙钟→UTC', () => {
  it('含跨日边界的换算', () => {
    expect(toUtcIso('2026-10-14 00:00:00')).toBe('2026-10-13T16:00:00.000Z')
    expect(toUtcIso('2026-10-13 09:00')).toBe('2026-10-13T01:00:00.000Z')
    expect(toUtcIso('2026-10-13')).toBe('2026-10-12T16:00:00.000Z')
  })

  it('拒绝非法格式', () => {
    expect(() => toUtcIso('2026/10/14 00:00:00')).toThrow()
    expect(() => toUtcIso('')).toThrow()
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

describe('shanghaiDayRange 上海自然日', () => {
  it('[当日零点, 次日零点) 半开区间', () => {
    expect(shanghaiDayRange('2026-10-14')).toEqual({
      startUtc: '2026-10-13T16:00:00.000Z',
      endUtc: '2026-10-14T16:00:00.000Z'
    })
  })

  it('跨月边界', () => {
    expect(shanghaiDayRange('2026-10-31').endUtc).toBe('2026-10-31T16:00:00.000Z')
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

describe('UTC↔上海往返', () => {
  it('往返一致', () => {
    const utc = toUtcIso('2026-03-01 08:30:00')
    expect(formatInstant(utc, { withSeconds: true })).toBe('2026-03-01 08:30:00')
  })
})

describe('toShOffsetIso', () => {
  it('Date 按上海墙钟输出 +08:00 偏移入参', () => {
    expect(toShOffsetIso(new Date('2026-10-13T01:00:00.000Z'))).toBe('2026-10-13T09:00:00+08:00')
  })

  it('UTC 16:00 输出为上海次日 00:00（跨日进位）', () => {
    expect(toShOffsetIso(new Date('2026-10-13T16:00:00.000Z'))).toBe('2026-10-14T00:00:00+08:00')
  })

  it('非法输入返回 null', () => {
    expect(toShOffsetIso(null)).toBeNull()
    expect(toShOffsetIso('2026-10-13T09:00:00Z')).toBeNull()
    expect(toShOffsetIso(new Date('invalid'))).toBeNull()
  })
})
