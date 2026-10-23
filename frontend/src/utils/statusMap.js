/**
 * 状态/类型枚举 → 展示配置（label + el-tag type）
 * 取值与后端 com.labmanage.module.item.enums 保持一致，未知值原样灰色展示
 */

/** 物料预约单状态（单级审核：老师预约创建即「老师已审核」，由管理员终审；
 *  部分实发不设派生态——实发进度看行明细，全部行发完才推进「全部实发」） */
export const requestStatusMap = {
  TEACHER_APPROVED: { label: '老师已审核', type: 'primary' },
  APPROVED: { label: '已批准', type: 'primary' },
  FULFILLED: { label: '全部实发', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

/** 物料预约申领类型（TEACHER 老师预约 / PROXY 学生代理代领；
 *  代领只能引用老师端已审核通过的老师预约单，免老师级审核、直接交管理员终审） */
export const requestTypeMap = {
  TEACHER: { label: '老师预约', type: 'primary' },
  PROXY: { label: '学生代理代领', type: 'warning' }
}

/** 场地预约单状态（新建物料预约选择关联预约时展示用；仅待审批/已批准可关联） */
export const bookingStatusMap = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已批准', type: 'primary' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

/** 物料结算类型 */
export const settlementTypeMap = {
  RETURNED: { label: '归还', type: 'success' },
  CONSUMED: { label: '耗用', type: 'warning' },
  LOST: { label: '丢失', type: 'danger' },
  DAMAGED: { label: '损坏', type: 'danger' }
}

/** 库存流水方向 */
export const moveDirectionMap = {
  IN: { label: '入库', type: 'success' },
  OUT: { label: '出库', type: 'danger' }
}

/** 库存预警级别 */
export const warningLevelMap = {
  NORMAL: { label: '正常', type: 'success' },
  WARNING: { label: '预警', type: 'warning' },
  OUT_OF_STOCK: { label: '缺货', type: 'danger' }
}

/** 物料类型 */
export const materialTypeMap = {
  CONSUMABLE: { label: '耗材', type: 'warning' },
  RETURNABLE: { label: '可回收', type: 'primary' }
}

/**
 * 从映射中取展示配置；空值显示 '-'，未知值原样灰色展示
 * @param {Record<string, {label: string, type: string}>} map
 * @param {string} value 枚举值
 */
export function statusOf(map, value) {
  if (value === null || value === undefined || value === '') {
    return { label: '-', type: 'info' }
  }
  return map[value] || { label: String(value), type: 'info' }
}
