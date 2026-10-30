export const CLAIM_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'PARTIALLY_ISSUED', label: '部分实发' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'REJECTED', label: '已驳回' }
]

export const PICKUP_MODE_LABELS = {
  SELF: '本人领取',
  ASSISTANT: '学生代领'
}

export const RESERVATION_RULE_LABELS = {
  BOOKING_WINDOW: '按预约时段预占',
  SHANGHAI_DAY_LOW_TOTAL: '低保有资产按预约日全天预占'
}

export const DOCUMENT_TYPE_LABELS = {
  ITEM_CLAIM: '申领单',
  ITEM_CLAIM_ISSUE: '实发单',
  ITEM_CLAIM_RETURN: '归还单',
  ISSUE: '实发单',
  RETURN: '归还单',
  OUTBOUND: '出库单',
  INBOUND: '入库单',
  OPENING: '期初入库单',
  REPLENISHMENT: '补货入库单',
  TRANSFER: '移动单',
  TRANSFER_RECEIPT: '移动实收单',
  TRANSFER_RETURN: '移动退回单',
  TRANSFER_SETTLEMENT: '在途核销单',
  SCRAP: '在库报废单',
  ADJUST: '在库调整单',
  STOCKTAKE: '盘点单'
}

export const BUSINESS_SCENE_LABELS = {
  OPENING: '期初入库',
  REPLENISHMENT: '补货入库',
  CLAIM_ISSUE: '申领实发',
  CLAIM_RETURN: '申领归还',
  TRANSFER_SEND: '移动发出',
  TRANSFER_RECEIVE: '移动实收',
  TRANSFER_RETURN: '移动退回',
  TRANSFER_SETTLE: '在途核销',
  IN_STOCK_SETTLE: '在库核销'
}

export const SETTLEMENT_TYPE_LABELS = {
  CONSUMED: '教学损耗',
  LOST: '丢失',
  DAMAGED: '损坏',
  SCRAP: '报废',
  ADJUST: '状态调整'
}

export const ASSET_STATUS_LABELS = {
  IDLE: '在库',
  ON_LOAN: '借出中',
  MAINTENANCE: '维修中',
  IN_TRANSIT: '在途',
  LOST: '已遗失',
  SCRAPPED: '已报废'
}

export const EVENT_TYPE_LABELS = {
  SUBMIT: '提交申领',
  MODIFY: '修改并重新提交',
  APPROVE: '审核通过',
  REJECT: '审核驳回',
  CANCEL: '取消申领',
  ABANDON: '申请放弃剩余',
  CLOSE: '关闭申领',
  EXPIRE: '申领过期',
  DELEGATE: '委派学生代领',
  REDELEGATE: '改派代领学生',
  UNDELEGATE: '撤回学生代领',
  POST: '单据过账',
  STOCK_OUT: '实发出库',
  STOCK_IN: '归还入库',
  ISSUE: '实发',
  RETURN: '归还',
  RECEIVE: '接收',
  REVIEW: '复核',
  TRANSFER_SEND: '移动发出',
  TRANSFER_RECEIVE: '移动实收',
  TRANSFER_RETURN: '移动退回',
  TRANSFER_RETURN_REQUEST: '申请移动退回',
  TRANSFER_SETTLEMENT_CREATE: '提交在途核销',
  TRANSFER_SETTLEMENT_REVIEW: '复核在途核销',
  SETTLEMENT_SUBMIT: '提交核销',
  SETTLEMENT_REVIEW: '复核核销',
  REVIEW_TASK_READY: '生成复核任务'
}

export function pickupModeLabel(mode) {
  return PICKUP_MODE_LABELS[mode] || mode || '—'
}

export function reservationRuleLabel(rule) {
  return RESERVATION_RULE_LABELS[rule] || rule || '—'
}

export function documentTypeLabel(type) {
  return DOCUMENT_TYPE_LABELS[type] || '库存单据'
}

export function businessSceneLabel(scene) {
  return BUSINESS_SCENE_LABELS[scene] || '其他业务'
}

export function settlementTypeLabel(type) {
  return SETTLEMENT_TYPE_LABELS[type] || '其他处理'
}

export function assetStatusLabel(status) {
  return ASSET_STATUS_LABELS[status] || '未知状态'
}

export function eventTypeLabel(type) {
  return EVENT_TYPE_LABELS[type] || '状态变更'
}

export function stockEventTypeLabel(type, scene) {
  if (type === 'STOCK_IN' && scene !== 'CLAIM_RETURN') return '入库'
  if (type === 'STOCK_OUT' && scene !== 'CLAIM_ISSUE') return '出库'
  return eventTypeLabel(type)
}
