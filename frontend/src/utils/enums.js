/**
 * 业务枚举展示映射（取值与后端 enums 包一致）：
 * label 为中文文案，pill 为 base.css 状态胶囊修饰类。
 * 前后端契约约定枚举以 name 传输，这里只做展示层映射。
 */

/** 借用单状态 */
export const BOOKING_STATUS = {
  PENDING_TEACHER: { label: '待老师审批', pill: 'pill--warn' },
  PENDING_ADMIN: { label: '待管理员审批', pill: 'pill--warn' },
  APPROVED: { label: '待借出', pill: 'pill--brand' },
  CHECKED_OUT: { label: '借出中', pill: 'pill--brand' },
  RETURNED: { label: '已归还', pill: 'pill--ok' },
  REJECTED: { label: '已驳回', pill: 'pill--danger' },
  CANCELLED: { label: '已撤销', pill: 'pill--muted' }
}

/** 借用单状态筛选项（保持枚举定义顺序） */
export const BOOKING_STATUS_OPTIONS = Object.entries(BOOKING_STATUS).map(([value, meta]) => ({
  value,
  label: meta.label
}))

/** 设备物理状态 */
export const EQUIPMENT_STATUS = {
  IDLE: { label: '空闲', pill: 'pill--ok' },
  RESERVED: { label: '已预约', pill: 'pill--brand' },
  IN_USE: { label: '使用中', pill: 'pill--brand' },
  MAINTAINING: { label: '维护中', pill: 'pill--warn' },
  SCRAPPED: { label: '已报废', pill: 'pill--muted' }
}

/** 设备状态筛选项 */
export const EQUIPMENT_STATUS_OPTIONS = Object.entries(EQUIPMENT_STATUS).map(([value, meta]) => ({
  value,
  label: meta.label
}))

/** 设备价值分级（NULL = 待分类，禁止借用，由调用方自行处理空值） */
export const VALUE_LEVEL = {
  NORMAL: { label: '普通', pill: 'pill--muted' },
  VALUABLE: { label: '贵重', pill: 'pill--warn' }
}

/**
 * 取枚举展示元数据：{ label, pill }
 * 未登记或空值回退 muted，fallbackLabel 用于「待分类」等业务空值文案
 */
export function enumMeta(value, map, fallbackLabel = '—') {
  return map[value] || { label: fallbackLabel, pill: 'pill--muted' }
}
