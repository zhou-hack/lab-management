import request from '../request'

/** 当前用户的物料通知分页 */
export function pageMyNotifications(params) {
  return request.get('/item/notifications/my', { params })
}

/** 当前用户的未读物料通知数 */
export function getUnreadNotificationCount() {
  return request.get('/item/notifications/unread_count')
}

export function readNotification(id) {
  return request.post(`/item/notifications/${id}/read`)
}

export function readAllNotifications() {
  return request.post('/item/notifications/read_all')
}
