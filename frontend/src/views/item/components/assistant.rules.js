import { toUtcIso } from '@/utils/datetime'

export function buildAssistantGrantPayload(form) {
  if (!form.studentId) throw new Error('请选择代领学生')
  if (!Array.isArray(form.timeRange) || form.timeRange.length !== 2) {
    throw new Error('请选择授权生效与失效时间')
  }
  const startAt = toUtcIso(form.timeRange[0])
  const endAt = toUtcIso(form.timeRange[1])
  if (Date.parse(startAt) >= Date.parse(endAt)) {
    throw new Error('失效时间必须晚于生效时间')
  }
  const reason = form.reason?.trim()
  return {
    studentId: String(form.studentId),
    startAt,
    endAt,
    ...(reason ? { reason } : {})
  }
}

export function assistantStatusLabel(status) {
  return {
    ACTIVE: '生效中',
    EXPIRED: '已失效',
    REVOKED: '已撤销'
  }[status] || status || '—'
}
