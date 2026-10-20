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

/** 标准场地名称词条：联想补全与命名规范参考 */
export const SPACE_NAME_KEYWORDS = [
  '实训室',
  '教室',
  '网络实训室',
  '电子电工实训室',
  '网络安全实训室',
  '软件开发实训室',
  '大数据实训室',
  '物联网实训室',
  '人工智能实训室',
  '云计算实训室',
  '智能制造实训室',
  '嵌入式实训室',
  '平面设计实训室',
  '影视后期实训室',
  '多媒体教室',
  '智慧教室',
  '普通教室',
  '研讨教室',
  '录播教室',
  '阶梯教室'
]

/**
 * 构建场地名称联想候选：已有场地名称（模糊包含）+ 标准名称词条。
 * @param {string} query 用户输入
 * @param {Array} options /space/spaces/options 返回的场地选项
 * @param {{ excludeId?: string, limit?: number }} opts 编辑时排除当前场地
 * @returns {Array<{ value: string, meta: string, kind: string }>}
 */
export function buildSpaceSuggestions(query, options, opts = {}) {
  const keyword = String(query || '').trim().toLowerCase()
  if (!keyword) return []
  const { excludeId = null, limit = 20 } = opts

  const names = []
  for (const option of options || []) {
    if (excludeId && String(option.id) === String(excludeId)) continue
    const name = option.labName || ''
    if (!name.toLowerCase().includes(keyword)) continue
    names.push({
      value: name,
      kind: 'space',
      meta: [
        SPACE_TYPE_MAP[option.labType]?.label,
        option.capacity ? `${option.capacity} 人` : '',
        SPACE_STATUS_MAP[option.status]?.label
      ]
        .filter(Boolean)
        .join(' · ')
    })
  }

  const keywords = SPACE_NAME_KEYWORDS
    .filter((word) => word.toLowerCase().includes(keyword))
    .filter((word) => !names.some((item) => item.value === word))
    .map((word) => ({ value: word, kind: 'keyword', meta: '标准名称词条' }))

  return [...names, ...keywords]
    .sort((a, b) => {
      const aPrefix = a.value.toLowerCase().startsWith(keyword) ? 0 : 1
      const bPrefix = b.value.toLowerCase().startsWith(keyword) ? 0 : 1
      if (aPrefix !== bPrefix) return aPrefix - bPrefix
      return a.value.localeCompare(b.value, 'zh-Hans-CN')
    })
    .slice(0, limit)
}
