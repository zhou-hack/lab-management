import { defineStore } from 'pinia'
import {
  getUnreadNotificationCount,
  pageMyNotifications,
  readAllNotifications,
  readNotification
} from '@/api/item/notification'
import { useUserStore } from '@/store/user'
import { hasPermission } from '@/utils/permission'

function isAuthOrPermissionError(error) {
  const status = error?.response?.status || error?.status
  const code = error?.response?.data?.code || error?.code
  return [401, 403, 40100, 40300].includes(status)
    || [40100, 40300].includes(code)
    || /无权限|没有权限|未登录|登录已失效/.test(error?.message || '')
}

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unreadCount: 0,
    drawerVisible: false,
    notifications: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
    unreadOnly: false,
    loading: false,
    unreadLoading: false,
    readingId: '',
    readAllLoading: false,
    // 无权限或登录失效后停止后续请求，避免轮询持续触发 401/403。
    authBlocked: false
  }),
  getters: {
    canList: () => hasPermission('item:notification:list', useUserStore().permissions),
    canRead: () => hasPermission('item:notification:read', useUserStore().permissions)
  },
  actions: {
    blockOnAuthError(error) {
      if (isAuthOrPermissionError(error)) this.authBlocked = true
    },
    async fetchUnreadCount() {
      if (!this.canList || this.authBlocked || this.unreadLoading) return false
      this.unreadLoading = true
      try {
        const count = await getUnreadNotificationCount()
        this.unreadCount = Math.max(0, Number(count) || 0)
        return true
      } catch (error) {
        this.blockOnAuthError(error)
        return false
      } finally {
        this.unreadLoading = false
      }
    },
    async fetchNotifications(pageNum = this.pageNum) {
      if (!this.canList || this.authBlocked || this.loading) return false
      this.loading = true
      try {
        const result = await pageMyNotifications({
          pageNum,
          pageSize: this.pageSize,
          unread: this.unreadOnly || undefined
        })
        this.notifications = result?.records || []
        this.total = Math.max(0, Number(result?.total) || 0)
        this.pageNum = Number(result?.pageNum) || pageNum
        return true
      } catch (error) {
        this.blockOnAuthError(error)
        return false
      } finally {
        this.loading = false
      }
    },
    openDrawer() {
      if (!this.canList) return Promise.resolve([false, false])
      this.drawerVisible = true
      return Promise.all([this.fetchNotifications(1), this.fetchUnreadCount()])
    },
    closeDrawer() {
      this.drawerVisible = false
    },
    changePage(pageNum) {
      return this.fetchNotifications(pageNum)
    },
    changeUnreadOnly(unreadOnly) {
      this.unreadOnly = unreadOnly
      return this.fetchNotifications(1)
    },
    async read(id) {
      if (!this.canList || !this.canRead || this.authBlocked || !id) return false
      const existing = this.notifications.find((item) => String(item.id) === String(id))
      if (existing?.readAt) return true
      if (this.readingId) return false
      this.readingId = String(id)
      try {
        await readNotification(id)
        const readAt = new Date().toISOString()
        if (this.unreadOnly) {
          this.notifications = this.notifications.filter((item) => String(item.id) !== String(id))
          this.total = Math.max(0, this.total - 1)
        } else if (existing) {
          existing.readAt = readAt
        }
        this.unreadCount = Math.max(0, this.unreadCount - 1)
        if (this.unreadOnly && !this.notifications.length && this.total > 0) {
          const lastPage = Math.max(1, Math.ceil(this.total / this.pageSize))
          await this.fetchNotifications(Math.min(this.pageNum, lastPage))
        }
        return true
      } catch (error) {
        this.blockOnAuthError(error)
        return false
      } finally {
        this.readingId = ''
      }
    },
    async readAll() {
      if (!this.canList || !this.canRead || this.authBlocked || this.readAllLoading || !this.unreadCount) {
        return false
      }
      this.readAllLoading = true
      try {
        await readAllNotifications()
        const readAt = new Date().toISOString()
        this.notifications = this.unreadOnly
          ? []
          : this.notifications.map((item) => ({ ...item, readAt: item.readAt || readAt }))
        if (this.unreadOnly) {
          this.total = 0
          this.pageNum = 1
        }
        this.unreadCount = 0
        return true
      } catch (error) {
        this.blockOnAuthError(error)
        return false
      } finally {
        this.readAllLoading = false
      }
    },
    reset() {
      this.unreadCount = 0
      this.drawerVisible = false
      this.notifications = []
      this.total = 0
      this.pageNum = 1
      this.unreadOnly = false
      this.loading = false
      this.unreadLoading = false
      this.readingId = ''
      this.readAllLoading = false
      this.authBlocked = false
    }
  }
})
