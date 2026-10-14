/**
 * 场地模块前端字典（与后端枚举、docs 数据字典保持一致）。
 * Q1 定稿（2026-10-13）：4 类 + OTHER；OTHER 可建档但不开放预约。
 */
export const LAB_TYPES = [
  { value: 'TRAINING_ROOM', label: '实训室', bookable: true },
  { value: 'COMPUTER_ROOM', label: '机房', bookable: true },
  { value: 'MULTIMEDIA_ROOM', label: '多媒体教室', bookable: true },
  { value: 'ELECTRONIC_LAB', label: '电子电工实训室', bookable: true },
  { value: 'OTHER', label: '其他/待分类', bookable: false }
]

export const LAB_TYPE_MAP = LAB_TYPES.reduce((map, item) => {
  map[item.value] = item
  return map
}, {})

/** 派生状态（Q2：不持久化，由后端实时计算后下发） */
export const LAB_STATUS = [
  { value: 'AVAILABLE', label: '可用', pill: 'pill--ok' },
  { value: 'OCCUPIED', label: '占用中', pill: 'pill--warn' },
  { value: 'MAINTAINING', label: '维护中', pill: 'pill--danger' },
  { value: 'DISABLED', label: '停用', pill: 'pill--muted' }
]

export const LAB_STATUS_MAP = LAB_STATUS.reduce((map, item) => {
  map[item.value] = item
  return map
}, {})

export const WEEKDAYS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]

export const weekdayLabel = (value) => WEEKDAYS.find((d) => d.value === value)?.label || '—'
