<template>
  <div class="workspace">
    <header class="ws-header">
      <div>
        <h1 class="ws-title">
          物料管理
        </h1>
        <p class="ws-subtitle">
          库存余额 / 台账 / 单据 / 申领定额 / 核销 / 盘点 / 基础数据管理；
          登录模块未接入，数据经 Vite 代理直连后端 /api/v1
        </p>
      </div>
    </header>

    <el-tabs
      v-model="active"
      class="ws-tabs"
    >
      <el-tab-pane
        label="库存余额"
        name="balance"
        lazy
      >
        <BalancePanel ref="balanceRef" />
      </el-tab-pane>
      <el-tab-pane
        label="库存流水"
        name="movement"
        lazy
      >
        <MovementPanel ref="movementRef" />
      </el-tab-pane>
      <el-tab-pane
        label="库存单据"
        name="document"
        lazy
      >
        <DocumentPanel ref="documentRef" />
      </el-tab-pane>
      <el-tab-pane
        label="申领单"
        name="request"
        lazy
      >
        <RequestPanel ref="requestRef" />
      </el-tab-pane>
      <el-tab-pane
        label="领用定额"
        name="quota"
        lazy
      >
        <QuotaPanel ref="quotaRef" />
      </el-tab-pane>
      <el-tab-pane
        label="物料核销"
        name="settlement"
        lazy
      >
        <SettlementPanel ref="settlementRef" />
      </el-tab-pane>
      <el-tab-pane
        label="盘点管理"
        name="stocktake"
        lazy
      >
        <StocktakePanel ref="stocktakeRef" />
      </el-tab-pane>
      <el-tab-pane
        label="基础数据"
        name="basic"
        lazy
      >
        <BasicDataPanel ref="basicRef" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import BalancePanel from './panels/BalancePanel.vue'
import BasicDataPanel from './panels/BasicDataPanel.vue'
import DocumentPanel from './panels/DocumentPanel.vue'
import MovementPanel from './panels/MovementPanel.vue'
import QuotaPanel from './panels/QuotaPanel.vue'
import RequestPanel from './panels/RequestPanel.vue'
import SettlementPanel from './panels/SettlementPanel.vue'
import StocktakePanel from './panels/StocktakePanel.vue'

const active = ref('balance')

const balanceRef = ref(null)
const movementRef = ref(null)
const documentRef = ref(null)
const requestRef = ref(null)
const quotaRef = ref(null)
const settlementRef = ref(null)
const stocktakeRef = ref(null)
const basicRef = ref(null)

const panelRefs = {
  balance: balanceRef,
  movement: movementRef,
  document: documentRef,
  request: requestRef,
  quota: quotaRef,
  settlement: settlementRef,
  stocktake: stocktakeRef,
  basic: basicRef
}

// 切换到某个页签时刷新对应面板，保证每次进入看到最新数据
// （页签为懒加载，首次切入时组件挂载会自行加载）
watch(
  active,
  (name) => {
    const panel = panelRefs[name]
    if (panel && panel.value) {
      panel.value.refresh()
    }
  },
  { flush: 'post' }
)
</script>

<style scoped>
.workspace {
  max-width: 1440px;
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

.ws-tabs {
  margin-top: 8px;
}
</style>

<style>
/* 物料工作台公共小样式（随工作台全局加载） */

/* 顶部标签栏：标签宽度自适应文字完整展示，禁止压缩换行造成的截断；
   窄屏溢出时 nav 保持内容实际宽度，Element Plus 据此自动渲染左右滚动箭头 */
.ws-tabs .el-tabs__item {
  white-space: nowrap;
  flex-shrink: 0;
}

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
   最小保底防止箭头与短标签拥挤，选中长文本时自动撑宽 */
.toolbar .el-select {
  width: fit-content;
  min-width: 96px;
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
