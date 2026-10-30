import { hasPermission } from '@/utils/permission'

export function getNotificationRoute(notification, permissions) {
  if (notification?.notificationType === 'CLAIM_REVIEW_PENDING') {
    return { path: '/admin/item/workspace', query: { tab: 'claims' } }
  }
  if (['CLAIM_APPROVED', 'RETURN_REMINDER'].includes(notification?.notificationType)) {
    return { path: '/item/claims', query: { tab: 'claims' } }
  }
  if (notification?.notificationType === 'PICKUP_DELEGATED') {
    return { path: '/item/pickups' }
  }
  return {
    path: hasPermission('item:claim:review', permissions)
      ? '/admin/item/workspace'
      : '/item/claims',
    query: { tab: 'overdues' }
  }
}

export function getNotificationLabel(notification) {
  if (notification?.notificationType === 'CLAIM_REVIEW_PENDING') return '待审批'
  if (notification?.notificationType === 'CLAIM_APPROVED') return '审批通过'
  if (notification?.notificationType === 'RETURN_REMINDER') {
    return notification.notificationStage === 'TWO_HOURLY' ? '每2小时归还提醒' : '归还提醒'
  }
  if (notification?.notificationType === 'PICKUP_DELEGATED') return '代领任务'
  return notification?.notificationStage === 'DAILY' ? '每日逾期提醒' : '逾期提醒'
}
