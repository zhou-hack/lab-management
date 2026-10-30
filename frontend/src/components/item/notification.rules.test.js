import { describe, expect, it } from 'vitest'
import { getNotificationLabel, getNotificationRoute } from './notification.rules'

describe('通知跳转目标', () => {
  it('待审批通知进入物料申领审核页签', () => {
    expect(getNotificationRoute({ notificationType: 'CLAIM_REVIEW_PENDING' }, ['item:claim:review']))
      .toEqual({ path: '/admin/item/workspace', query: { tab: 'claims' } })
  })

  it('批准和归还提醒进入自己的申领页签', () => {
    for (const notificationType of ['CLAIM_APPROVED', 'RETURN_REMINDER']) {
      expect(getNotificationRoute({ notificationType }, ['item:notification:list']))
        .toEqual({ path: '/item/claims', query: { tab: 'claims' } })
    }
  })

  it('代领通知进入我的代领页面', () => {
    expect(getNotificationRoute({ notificationType: 'PICKUP_DELEGATED' }, ['item:notification:list']))
      .toEqual({ path: '/item/pickups' })
  })

  it('逾期通知保持管理员和普通用户原有跳转', () => {
    const notification = { notificationType: 'OVERDUE_UNRETURNED' }
    expect(getNotificationRoute(notification, ['item:claim:review'])).toEqual({
      path: '/admin/item/workspace', query: { tab: 'overdues' }
    })
    expect(getNotificationRoute(notification, ['item:notification:list'])).toEqual({
      path: '/item/claims', query: { tab: 'overdues' }
    })
  })
})

describe('通知类型标签', () => {
  it('按业务类型和阶段显示中文标签', () => {
    expect(getNotificationLabel({ notificationType: 'CLAIM_REVIEW_PENDING' })).toBe('待审批')
    expect(getNotificationLabel({ notificationType: 'CLAIM_APPROVED' })).toBe('审批通过')
    expect(getNotificationLabel({ notificationType: 'RETURN_REMINDER' })).toBe('归还提醒')
    expect(getNotificationLabel({ notificationType: 'RETURN_REMINDER', notificationStage: 'TWO_HOURLY' }))
      .toBe('每2小时归还提醒')
    expect(getNotificationLabel({ notificationType: 'PICKUP_DELEGATED' })).toBe('代领任务')
    expect(getNotificationLabel({ notificationType: 'OVERDUE_UNRETURNED' })).toBe('逾期提醒')
    expect(getNotificationLabel({ notificationType: 'OVERDUE_UNRETURNED', notificationStage: 'DAILY' }))
      .toBe('每日逾期提醒')
  })
})
