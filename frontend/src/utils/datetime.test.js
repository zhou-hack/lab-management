import { describe, expect, it } from 'vitest'
import { formatSh, shWallClockToUtcIso } from './datetime'

describe('shWallClockToUtcIso', () => {
  it('上海上午 10 点 → UTC 02:00（同一日）', () => {
    expect(shWallClockToUtcIso('2026-10-14 10:00')).toBe('2026-10-14T02:00:00.000Z')
  })

  it('上海 02:00 → UTC 前一日 18:00（跨日回退）', () => {
    expect(shWallClockToUtcIso('2026-10-14 02:00')).toBe('2026-10-13T18:00:00.000Z')
  })

  it('上海午夜 00:00 → UTC 前一日 16:00（业务日界线）', () => {
    expect(shWallClockToUtcIso('2026-10-14 00:00')).toBe('2026-10-13T16:00:00.000Z')
  })

  it('往返一致：formatSh(shWallClockToUtcIso(x)) === x', () => {
    const wall = '2026-10-13 09:05'
    expect(formatSh(shWallClockToUtcIso(wall))).toBe(wall)
  })

  it('带 T 分隔符的墙钟同样可解析', () => {
    expect(shWallClockToUtcIso('2026-10-14T10:00')).toBe('2026-10-14T02:00:00.000Z')
  })

  it('非法输入抛错（无偏移量字符串不进 new Date）', () => {
    expect(() => shWallClockToUtcIso('2026-10-14')).toThrow()
    expect(() => shWallClockToUtcIso('10:00')).toThrow()
    expect(() => shWallClockToUtcIso('')).toThrow()
    expect(() => shWallClockToUtcIso(null)).toThrow()
  })
})

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
