/**
 * 场地模块前端字典（与后端枚举、docs 数据字典保持一致）。
 * lab 单表定稿（2026-10-14）：类型二值、状态三值落库。
 */

/** 场地类型（SHORT 记号：Java 枚举映射，落库 VARCHAR(32)） */
export const SPACE_TYPES = [
  { value: 'CLASSROOM', label: '教室' },
  { value: 'TRAINING_ROOM', label: '实训室' }
]

export const SPACE_TYPE_MAP = SPACE_TYPES.reduce((map, item) => {
  map[item.value] = item
  return map
}, {})

/**
 * 场地状态（lab.status 落库三值）。
 * 占用为预约模块派生态，不在本表落库。
 */
export const SPACE_STATUS = [
  { value: 'AVAILABLE', label: '可用', pill: 'pill--ok' },
  { value: 'MAINTAINING', label: '维护中', pill: 'pill--warn' },
  { value: 'CLOSED', label: '停用', pill: 'pill--muted' }
]

export const SPACE_STATUS_MAP = SPACE_STATUS.reduce((map, item) => {
  map[item.value] = item
  return map
}, {})
