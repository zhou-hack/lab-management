<template>
  <div class="workspace">
    <header class="ws-header">
      <div>
        <h1 class="ws-title">
          物料管理
        </h1>
        <p class="ws-subtitle">
          库存余额 / 台账 / 单据 / 申领定额 / 核销 / 基础数据管理；
          登录模块未接入，数据经 Vite 代理直连后端 /api/v1
        </p>
      </div>
    </header>

    <div class="ws-body">
      <aside class="ws-side">
        <el-menu
          class="ws-menu"
          :default-active="active"
          :default-openeds="openGroups"
          @select="onMenuSelect"
        >
          <el-sub-menu
            v-for="group in MENU"
            :key="group.key"
            :index="group.key"
          >
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.label }}</span>
            </template>
            <el-menu-item
              v-for="leaf in group.children"
              :key="leaf.key"
              :index="leaf.key"
            >
              {{ leaf.label }}
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </aside>

      <main class="ws-main">
        <div class="ws-main-title">
          {{ activeLabel }}
        </div>
        <BalancePanel
          v-if="visited.balance"
          v-show="active === 'balance'"
          ref="balanceRef"
        />
        <MovementPanel
          v-if="visited.movement"
          v-show="active === 'movement'"
          ref="movementRef"
        />
        <DocumentPanel
          v-if="visited.document"
          v-show="active === 'document'"
          ref="documentRef"
        />
        <RequestPanel
          v-if="visited.request"
          v-show="active === 'request'"
          ref="requestRef"
        />
        <QuotaPanel
          v-if="visited.quota"
          v-show="active === 'quota'"
          ref="quotaRef"
        />
        <SettlementPanel
          v-if="visited.settlement"
          v-show="active === 'settlement'"
          ref="settlementRef"
        />
        <CategorySection
          v-if="visited.basic_category"
          v-show="active === 'basic_category'"
          ref="categoryRef"
        />
        <SkuSection
          v-if="visited.basic_sku"
          v-show="active === 'basic_sku'"
          ref="skuRef"
        />
        <LocationSection
          v-if="visited.basic_location"
          v-show="active === 'basic_location'"
          ref="locationRef"
        />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { DataAnalysis, List, Setting, Tickets } from '@element-plus/icons-vue'
import BalancePanel from './panels/BalancePanel.vue'
import CategorySection from './panels/basic/CategorySection.vue'
import DocumentPanel from './panels/DocumentPanel.vue'
import LocationSection from './panels/basic/LocationSection.vue'
import MovementPanel from './panels/MovementPanel.vue'
import QuotaPanel from './panels/QuotaPanel.vue'
import RequestPanel from './panels/RequestPanel.vue'
import SettlementPanel from './panels/SettlementPanel.vue'
import SkuSection from './panels/basic/SkuSection.vue'

/**
 * 物料管理工作台：左侧一级/二级菜单 + 右侧内容区
 * 菜单结构（一级=业务分组，二级=页面）：
 *   基础数据：物料分类 / 物料档案 / 库位管理（原内部页签摊平为二级菜单）
 *   库存查询：库存余额 / 库存流水
 *   库存业务：库存单据 / 申领单
 *   定额核销：领用定额 / 物料核销
 * 面板懒挂载：首次进入才挂载（v-if），此后切回仅切换显示（v-show）保留筛选与分页状态
 */
const MENU = [
  {
    key: 'basic',
    label: '基础数据',
    icon: Setting,
    children: [
      { key: 'basic_category', label: '物料分类' },
      { key: 'basic_sku', label: '物料档案' },
      { key: 'basic_location', label: '库位管理' }
    ]
  },
  {
    key: 'query',
    label: '库存查询',
    icon: DataAnalysis,
    children: [
      { key: 'balance', label: '库存余额' },
      { key: 'movement', label: '库存流水' }
    ]
  },
  {
    key: 'business',
    label: '库存业务',
    icon: Tickets,
    children: [
      { key: 'document', label: '库存单据' },
      { key: 'request', label: '申领单' }
    ]
  },
  {
    key: 'settle',
    label: '定额核销',
    icon: List,
    children: [
      { key: 'quota', label: '领用定额' },
      { key: 'settlement', label: '物料核销' }
    ]
  }
]

/** 一级分组默认全部展开，二级页面直接可见 */
const openGroups = MENU.map((group) => group.key)

const active = ref('balance')

/** 已访问过的页面（懒挂载标记）：初始页随工作台挂载 */
const visited = reactive({ balance: true })

const balanceRef = ref(null)
const movementRef = ref(null)
const documentRef = ref(null)
const requestRef = ref(null)
const quotaRef = ref(null)
const settlementRef = ref(null)
const categoryRef = ref(null)
const skuRef = ref(null)
const locationRef = ref(null)

const panelRefs = {
  balance: balanceRef,
  movement: movementRef,
  document: documentRef,
  request: requestRef,
  quota: quotaRef,
  settlement: settlementRef,
  basic_category: categoryRef,
  basic_sku: skuRef,
  basic_location: locationRef
}

function onMenuSelect(key) {
  active.value = key
}

/** 内容区页面标题：原页签标签移入侧边后，由内容区补足当前页面名 */
const activeLabel = computed(() => {
  for (const group of MENU) {
    const leaf = group.children.find((c) => c.key === active.value)
    if (leaf) return leaf.label
  }
  return ''
})

// 切换页面时刷新对应面板，保证每次进入看到最新数据；
// 首次进入由挂载时自行加载，不重复请求（flush post 确保挂载完成后取 ref）
watch(
  active,
  (key) => {
    if (visited[key]) {
      const panel = panelRefs[key]
      if (panel && panel.value) {
        panel.value.refresh()
      }
    }
    visited[key] = true
  },
  { flush: 'post' }
)
</script>

<style scoped>
.workspace {
  max-width: 1680px;
  margin: 0 auto;
  padding: 20px 24px 32px;
}

.ws-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.ws-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.ws-subtitle {
  margin: 6px 0 0;
  font-size: 12px;
  color: #909399;
}

.ws-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-top: 8px;
}

.ws-side {
  width: 200px;
  flex-shrink: 0;
  position: sticky;
  top: 20px;
  background: var(--surface);
  border: .5px solid var(--hair);
  border-radius: 8px;
  padding: 6px 0;
}

.ws-menu {
  border-right: none;
}

/* 紧凑两级菜单：标题与菜单项统一 44px 行高 */
.ws-menu :deep(.el-sub-menu__title),
.ws-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
}

.ws-main {
  flex: 1;
  min-width: 0;
}

.ws-main-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--g1);
}
</style>

<style>
/* 物料工作台公共小样式（随工作台全局加载） */

/* 物料/库位远程搜索下拉（popper 挂载在 body，需全局样式）：
   长选项「编码 · 名称」换行完整展示，不省略截断 */
.item-select-popper .el-select-dropdown__item {
  white-space: normal;
  height: auto;
  min-height: 34px;
  padding-top: 7px;
  padding-bottom: 7px;
  line-height: 20px;
  word-break: break-all;
}

/* 工具栏固定单行：所有筛选控件横排一行，窄屏放不下时横向滚动，绝不换行 */
.toolbar {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  overflow-x: auto;
}

/* 单行布局下控件按内容占宽、禁止压缩，避免文字被挤压遮挡 */
.toolbar > * {
  flex-shrink: 0;
  white-space: nowrap;
}

/* 下拉宽度跟随选中项文字自适应（EP 默认 100% 会占满导致换行）；
   min-width 保底让「全部申领类型」等 4-6 字标签舒适展示，文字与箭头不挤压；
   max-width 上限制约长文本（如长分类名）防止撑爆工具栏触发横向滚动 */
.toolbar .el-select {
  width: max-content;
  min-width: 144px;
  max-width: 220px;
}

/* el-select 内部 input 默认 padding-right:30px 留给下拉箭头，
   选中长文本时文字会与箭头重叠遮挡，缩到 26px 并加 truncate 防遮挡 */
.toolbar .el-select .el-select__wrapper {
  padding-right: 26px;
}
.toolbar .el-select .el-select__selected-item {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* el-input 同样按内容自适应，但固定宽度的 w180/w220/w240 仍保留；
   用 :where() 降低此规则优先级，让具名宽度类（w180 等）胜出 */
.toolbar :where(.el-input) {
  width: max-content;
  min-width: 180px;
}

.toolbar .w180 {
  width: 180px;
}

.toolbar .w220 {
  width: 220px;
}

.toolbar .w240 {
  width: 240px;
}

.w240 {
  width: 240px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.muted {
  color: #909399;
}

.small {
  font-size: 12px;
}

.mt8 {
  margin-top: 8px;
}

.ml4 {
  margin-left: 4px;
}
</style>
