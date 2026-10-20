/**
 * 状态/类型枚举 → 展示配置（label + el-tag type）
 * 取值与后端 com.labmanage.module.item.enums 保持一致，未知值原样灰色展示
 */

/** 库存单据状态 */
export const documentStatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已批准', type: 'primary' },
  POSTED: { label: '已过账', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

/** 物料申领单状态（两级审核：SELF 经 草稿→老师已审核→已批准；PROXY 创建即老师已审核） */
export const requestStatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  TEACHER_APPROVED: { label: '老师已审核', type: 'primary' },
  APPROVED: { label: '已批准', type: 'primary' },
  PARTIALLY_FULFILLED: { label: '部分实发', type: 'warning' },
  FULFILLED: { label: '全部实发', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

/** 申领类型 */
export const requestTypeMap = {
  SELF: { label: '本人申领', type: 'primary' },
  PROXY: { label: '学生代理代领', type: 'warning' }
}

/** 盘点单状态 */
export const stocktakeStatusMap = {
  DRAFT: { label: '草稿', type: 'info' },
  COUNTING: { label: '盘点中', type: 'warning' },
  PENDING_REVIEW: { label: '待复核', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

/** 库存流水业务类型 */
export const movementTypeMap = {
  OPENING: { label: '期初入库', type: 'success' },
  PURCHASE_IN: { label: '采购入库', type: 'success' },
  ISSUE: { label: '领用出库', type: 'warning' },
  RETURN_IN: { label: '归还入库', type: 'success' },
  TRANSFER: { label: '调拨', type: 'primary' },
  ADJUST: { label: '盘点调账', type: 'primary' },
  SCRAP: { label: '报废', type: 'danger' },
  REVERSAL: { label: '冲销', type: 'danger' }
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
