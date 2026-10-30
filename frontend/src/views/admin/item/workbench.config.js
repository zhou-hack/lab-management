export const ITEM_WORKBENCH_TABS = [
  {
    key: 'materials',
    label: '物料档案',
    permissions: ['item:material:list'],
    componentPath: './panels/MaterialArchivePanel.vue',
    loader: () => import('./panels/MaterialArchivePanel.vue')
  },
  {
    key: 'consumables',
    label: '耗材库存',
    permissions: ['item:stock:list'],
    componentPath: './panels/ConsumableStockPanel.vue',
    loader: () => import('./panels/ConsumableStockPanel.vue')
  },
  {
    key: 'assets',
    label: '固定资产',
    permissions: ['item:asset:list'],
    componentPath: './panels/FixedAssetStockPanel.vue',
    loader: () => import('./panels/FixedAssetStockPanel.vue')
  },
  {
    key: 'locations',
    label: '库位管理',
    permissions: ['item:location:list'],
    componentPath: './panels/LocationPanel.vue',
    loader: () => import('./panels/LocationPanel.vue')
  },
  {
    key: 'responsible_history',
    label: '负责人变动',
    permissions: ['item:location:list'],
    componentPath: './panels/ResponsibleHistoryPanel.vue',
    loader: () => import('./panels/ResponsibleHistoryPanel.vue')
  },
  {
    key: 'inbound',
    label: '期初 / 补货入库',
    permissions: ['item:stock:inbound'],
    componentPath: './panels/InboundPanel.vue',
    loader: () => import('./panels/InboundPanel.vue')
  },
  {
    key: 'documents',
    label: '库存单据',
    permissions: ['item:document:list'],
    componentPath: './panels/DocumentCenterPanel.vue',
    loader: () => import('./panels/DocumentCenterPanel.vue')
  },
  {
    key: 'transfers',
    label: '库位移动',
    permissions: ['item:transfer:create', 'item:transfer:receive'],
    componentPath: './panels/TransferPanel.vue',
    loader: () => import('./panels/TransferPanel.vue')
  },
  {
    key: 'movements',
    label: '库存流水',
    permissions: ['item:movement:list'],
    componentPath: './panels/MovementPanel.vue',
    loader: () => import('./panels/MovementPanel.vue')
  },
  {
    key: 'claims',
    label: '申领审核',
    permissions: ['item:claim:review'],
    componentPath: './panels/ClaimReviewPanel.vue',
    loader: () => import('./panels/ClaimReviewPanel.vue')
  },
  {
    key: 'settlements',
    label: '库存核销',
    permissions: ['item:settlement:create', 'item:settlement:review'],
    componentPath: './panels/SettlementPanel.vue',
    loader: () => import('./panels/SettlementPanel.vue')
  },
  {
    key: 'overdues',
    label: '逾期记录',
    permissions: ['item:overdue:list'],
    componentPath: './panels/OverduePanel.vue',
    loader: () => import('./panels/OverduePanel.vue')
  }
]
