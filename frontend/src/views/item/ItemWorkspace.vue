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
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.toolbar .w140 {
  width: 140px;
}

.toolbar .w160 {
  width: 160px;
}

.toolbar .w200 {
  width: 200px;
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
