import { describe, expect, it } from 'vitest'
import {
  latestItems,
  mapBookingTodo,
  mapBookingUsage,
  mapClaimTodo,
  mapClaimUsage,
  mapDocumentUsage,
  mapPickupTodo
} from './home.rules'

describe('首页待办映射', () => {
  it('场地和物料待办进入对应处理页面', () => {
    expect(mapBookingTodo({ id: 1, purpose: '课程实验', applicantName: '王老师', labName: 'A703', createTime: '2026-10-29T01:00:00Z' }))
      .toMatchObject({
        title: '场地预约 · 课程实验',
        meta: '王老师 · A703',
        route: { path: '/admin/booking/approval' }
      })
    expect(mapClaimTodo({ id: 2, purpose: '元器件申请', applicantName: '王老师', documentNo: 'ITC2', createTime: '2026-10-29T02:00:00Z' }))
      .toMatchObject({
        title: '物料申领 · 元器件申请',
        meta: '王老师 · ITC2',
        route: { path: '/admin/item/workspace', query: { tab: 'claims' } }
      })
    expect(mapPickupTodo({ id: 3, purpose: '代领物料', applicantName: '王老师', documentNo: 'ITC3' }).route)
      .toEqual({ path: '/item/pickups' })
  })
})

describe('首页使用记录映射', () => {
  it('实发单据与场地使用保留真实业务信息及跳转', () => {
    expect(mapDocumentUsage({ id: 4, documentNo: 'IM4', locationName: 'A703', pickupUserName: '张明', occurredAt: '2026-10-29T03:00:00Z' }))
      .toMatchObject({
        title: '物料实发 · IM4',
        meta: 'A703 · 张明',
        time: '2026-10-29T03:00:00Z',
        route: { path: '/admin/item/workspace', query: { tab: 'documents' } }
      })
    expect(mapBookingUsage({ id: 5, labName: 'A703', applicantName: '王老师', runtimePhase: 'IN_USE' }, true))
      .toMatchObject({ meta: '王老师 · 使用中', route: { path: '/admin/booking/labs' } })
    expect(mapBookingUsage({ id: 6, labName: 'A703', applicantName: '王老师', runtimePhase: 'FINISHED' }, false).route)
      .toEqual({ path: '/booking/labs' })
    expect(mapClaimUsage({ id: 7, purpose: '元器件申请', documentNo: 'ITC7' }).route)
      .toEqual({ path: '/item/claims' })
  })
})

describe('首页记录排序', () => {
  it('按时间倒序合并并最多保留五条，忽略无效时间', () => {
    const items = [
      { id: 1, time: '2026-10-29T01:00:00Z' },
      { id: 2, time: '2026-10-29T03:00:00Z' },
      { id: 3, time: '2026-10-29T02:00:00Z' },
      { id: 4, time: 'invalid' },
      { id: 5, time: '2026-10-28T03:00:00Z' },
      { id: 6, time: '2026-10-28T02:00:00Z' },
      { id: 7, time: '2026-10-28T01:00:00Z' }
    ]

    expect(latestItems(items).map((item) => item.id)).toEqual([2, 3, 1, 5, 6])
    expect(latestItems(items, 2).map((item) => item.id)).toEqual([2, 3])
  })
})
