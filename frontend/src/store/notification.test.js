import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  permissions: [],
  getUnreadNotificationCount: vi.fn(),
  pageMyNotifications: vi.fn(),
  readAllNotifications: vi.fn(),
  readNotification: vi.fn()
}))

vi.mock('@/api/item/notification', () => ({
  getUnreadNotificationCount: mocks.getUnreadNotificationCount,
  pageMyNotifications: mocks.pageMyNotifications,
  readAllNotifications: mocks.readAllNotifications,
  readNotification: mocks.readNotification
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({ permissions: mocks.permissions })
}))

import { useNotificationStore } from './notification'

describe('notification store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mocks.permissions = ['item:notification:list', 'item:notification:read']
    mocks.getUnreadNotificationCount.mockResolvedValue(0)
    mocks.pageMyNotifications.mockResolvedValue({ records: [], total: 0, pageNum: 1 })
    mocks.readNotification.mockResolvedValue(undefined)
    mocks.readAllNotifications.mockResolvedValue(undefined)
  })

  it('仅在有通知权限时加载列表和未读数', async () => {
    mocks.permissions = []
    const store = useNotificationStore()

    expect(await store.fetchNotifications()).toBe(false)
    expect(await store.fetchUnreadCount()).toBe(false)
    expect(await store.openDrawer()).toEqual([false, false])
    expect(store.drawerVisible).toBe(false)
    expect(mocks.pageMyNotifications).not.toHaveBeenCalled()
    expect(mocks.getUnreadNotificationCount).not.toHaveBeenCalled()
  })

  it('加载未读数和分页通知并保存服务端结果', async () => {
    mocks.getUnreadNotificationCount.mockResolvedValue(6)
    mocks.pageMyNotifications.mockResolvedValue({
      records: [{ id: 1, title: '归还逾期' }],
      total: 16,
      pageNum: 2
    })
    const store = useNotificationStore()

    expect(await store.fetchUnreadCount()).toBe(true)
    expect(await store.fetchNotifications(2)).toBe(true)

    expect(store.unreadCount).toBe(6)
    expect(store.notifications).toEqual([{ id: 1, title: '归还逾期' }])
    expect(store.total).toBe(16)
    expect(store.pageNum).toBe(2)
    expect(mocks.pageMyNotifications).toHaveBeenCalledWith({
      pageNum: 2,
      pageSize: 10,
      unread: undefined
    })
  })

  it('单条已读会更新当前通知并扣减未读数', async () => {
    const store = useNotificationStore()
    store.notifications = [
      { id: 1, readAt: null },
      { id: 2, readAt: null }
    ]
    store.unreadCount = 2

    expect(await store.read(1)).toBe(true)

    expect(mocks.readNotification).toHaveBeenCalledWith(1)
    expect(store.notifications[0].readAt).toEqual(expect.any(String))
    expect(store.notifications[1].readAt).toBeNull()
    expect(store.unreadCount).toBe(1)
    expect(store.readingId).toBe('')
  })

  it('全部已读保留历史已读时间并清零未读数', async () => {
    const previousReadAt = '2026-10-27T01:00:00.000Z'
    const store = useNotificationStore()
    store.notifications = [
      { id: 1, readAt: null },
      { id: 2, readAt: previousReadAt }
    ]
    store.unreadCount = 2

    expect(await store.readAll()).toBe(true)

    expect(mocks.readAllNotifications).toHaveBeenCalledOnce()
    expect(store.notifications[0].readAt).toEqual(expect.any(String))
    expect(store.notifications[1].readAt).toBe(previousReadAt)
    expect(store.unreadCount).toBe(0)
    expect(store.readAllLoading).toBe(false)
  })

  it('403 后阻止后续通知请求', async () => {
    mocks.getUnreadNotificationCount.mockRejectedValueOnce({ response: { status: 403 } })
    const store = useNotificationStore()
    store.notifications = [{ id: 1, readAt: null }]
    store.unreadCount = 1

    expect(await store.fetchUnreadCount()).toBe(false)
    expect(store.authBlocked).toBe(true)
    expect(store.unreadLoading).toBe(false)

    expect(await store.fetchNotifications()).toBe(false)
    expect(await store.fetchUnreadCount()).toBe(false)
    expect(await store.read(1)).toBe(false)
    expect(await store.readAll()).toBe(false)
    expect(mocks.pageMyNotifications).not.toHaveBeenCalled()
    expect(mocks.getUnreadNotificationCount).toHaveBeenCalledOnce()
    expect(mocks.readNotification).not.toHaveBeenCalled()
    expect(mocks.readAllNotifications).not.toHaveBeenCalled()
  })
})
