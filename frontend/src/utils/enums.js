/**
 * 业务枚举展示映射（取值与后端 enums 包一致）：
 * label 为中文文案，pill 为 base.css 状态胶囊修饰类。
 * 前后端契约约定枚举以 name 传输，这里只做展示层映射。
 */

/** 设备物理状态 */
export const EQUIPMENT_STATUS = {
  IDLE: { label: '空闲', pill: 'pill--ok' },
  IN_USE: { label: '借出中', pill: 'pill--warn' },
  MAINTAINING: { label: '维护中', pill: 'pill--warn' },
  SCRAPPED: { label: '已报废', pill: 'pill--muted' }
}

/** 设备状态筛选项 */
export const EQUIPMENT_STATUS_OPTIONS = Object.entries(EQUIPMENT_STATUS).map(([value, meta]) => ({
  value,
  label: meta.label
}))

/** 设备使用申请单状态（学生单教师初审，超时管理员代批） */
export const BOOKING_STATUS = {
  PENDING_TEACHER: { label: '待教师初审', pill: 'pill--warn' },
  PENDING_ADMIN: { label: '待管理员终审', pill: 'pill--warn' },
  APPROVED: { label: '已批准', pill: 'pill--ok' },
  CHECKED_OUT: { label: '已借出', pill: 'pill--warn' },
  RETURNED: { label: '已归还', pill: 'pill--muted' },
  REJECTED: { label: '已驳回', pill: 'pill--danger' },
  CANCELLED: { label: '已撤销', pill: 'pill--muted' }
}

/** 申请状态筛选项 */
export const BOOKING_STATUS_OPTIONS = Object.entries(BOOKING_STATUS).map(([value, meta]) => ({
  value,
  label: meta.label
}))

/** 归还状态 */
export const RETURN_CONDITION = {
  NORMAL: { label: '完好', pill: 'pill--ok' },
  DAMAGED: { label: '损坏', pill: 'pill--danger' }
}

/**
 * 取枚举展示元数据：{ label, pill }
 * 未登记或空值回退 muted，fallbackLabel 用于「待分类」等业务空值文案
 */
export function enumMeta(value, map, fallbackLabel = '—') {
  return map[value] || { label: fallbackLabel, pill: 'pill--muted' }
}
