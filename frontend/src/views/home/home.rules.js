export function mapClaimTodo(claim) {
  return {
    id: `claim:${claim.id}`,
    title: `物料申领 · ${claim.purpose || claim.documentNo || claim.id}`,
    meta: [claim.applicantName || '申请人', claim.documentNo].filter(Boolean).join(' · '),
    time: claim.createTime,
    route: { path: '/admin/item/workspace', query: { tab: 'claims' } }
  }
}

export function mapBookingTodo(booking) {
  return {
    id: `booking:${booking.id}`,
    title: `场地预约 · ${booking.purpose || booking.labName || booking.id}`,
    meta: [booking.applicantName || '申请人', booking.labName].filter(Boolean).join(' · '),
    time: booking.createTime,
    route: { path: '/admin/booking/approval' }
  }
}

export function mapDocumentUsage(document) {
  return {
    id: `document:${document.id}`,
    title: `物料实发 · ${document.documentNo || document.id}`,
    meta: [document.locationName, document.pickupUserName || document.operatorName]
      .filter(Boolean).join(' · '),
    time: document.occurredAt || document.createTime,
    route: { path: '/admin/item/workspace', query: { tab: 'documents' } }
  }
}

export function mapBookingUsage(booking, admin = false) {
  return {
    id: `usage:${booking.id}`,
    title: `场地使用 · ${booking.labName || booking.purpose || booking.id}`,
    meta: [booking.applicantName || '使用人', booking.runtimePhase === 'IN_USE' ? '使用中' : '已结束']
      .filter(Boolean).join(' · '),
    time: booking.startAt || booking.createTime,
    route: { path: admin ? '/admin/booking/labs' : '/booking/labs' }
  }
}

export function mapClaimUsage(claim) {
  return {
    id: `claim-usage:${claim.id}`,
    title: `物料申领 · ${claim.purpose || claim.documentNo || claim.id}`,
    meta: claim.documentNo || '',
    time: claim.createTime,
    route: { path: '/item/claims' }
  }
}

export function mapPickupTodo(pickup) {
  return {
    id: `pickup:${pickup.id}`,
    title: `代领任务 · ${pickup.purpose || pickup.documentNo || pickup.id}`,
    meta: [pickup.applicantName || '老师', pickup.documentNo].filter(Boolean).join(' · '),
    time: pickup.delegatedAt || pickup.createTime,
    route: { path: '/item/pickups' }
  }
}

export function latestItems(items, limit = 5) {
  return items
    .filter((item) => item && Number.isFinite(Date.parse(item.time)))
    .sort((a, b) => Date.parse(b.time) - Date.parse(a.time))
    .slice(0, limit)
}
