import { describe, expect, it } from 'vitest'
import { assistantStatusLabel, buildAssistantGrantPayload } from './assistant.rules'

describe('助理授权提交规则', () => {
  it('将上海墙钟时间转换为 UTC ISO，并规范化学生 ID 与原因', () => {
    expect(buildAssistantGrantPayload({
      studentId: 23,
      timeRange: ['2026-10-28 08:00:00', '2026-10-28 10:30:00'],
      reason: '  课程实验代领  '
    })).toEqual({
      studentId: '23',
      startAt: '2026-10-28T00:00:00.000Z',
      endAt: '2026-10-28T02:30:00.000Z',
      reason: '课程实验代领'
    })
  })

  it('空原因不提交，缺少学生或完整时段时拦截', () => {
    const result = buildAssistantGrantPayload({
      studentId: '23',
      timeRange: ['2026-10-28 08:00', '2026-10-28 09:00'],
      reason: '   '
    })
    expect(result).not.toHaveProperty('reason')
    expect(() => buildAssistantGrantPayload({
      studentId: '',
      timeRange: ['2026-10-28 08:00', '2026-10-28 09:00']
    })).toThrow('请选择代领学生')
    expect(() => buildAssistantGrantPayload({ studentId: '23', timeRange: [] }))
      .toThrow('请选择授权生效与失效时间')
  })

  it('失效时间必须晚于生效时间', () => {
    expect(() => buildAssistantGrantPayload({
      studentId: '23',
      timeRange: ['2026-10-28 09:00', '2026-10-28 09:00']
    })).toThrow('失效时间必须晚于生效时间')
  })
})

describe('助理授权状态展示', () => {
  it('展示三种后端状态并保留未知状态', () => {
    expect(assistantStatusLabel('ACTIVE')).toBe('生效中')
    expect(assistantStatusLabel('EXPIRED')).toBe('已失效')
    expect(assistantStatusLabel('REVOKED')).toBe('已撤销')
    expect(assistantStatusLabel('OTHER')).toBe('OTHER')
  })
})
