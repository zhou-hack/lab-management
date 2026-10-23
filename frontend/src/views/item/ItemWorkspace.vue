<template>
  <div class="workspace">
    <header class="ws-header">
      <div>
        <h1 class="ws-title">
          物料管理
        </h1>
        <p class="ws-subtitle">
          基础数据 / 库存查询 / 库存业务（物料预约，页签含领用定额）/ 核销管理；
          分组导航见左侧菜单，登录模块未接入，数据经 Vite 代理直连后端 /api/v1
        </p>
      </div>
    </header>

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
      <RequestPanel
        v-if="visited.request"
        v-show="active === 'request'"
        ref="requestRef"
      />
      <SettlementPanel
        v-if="visited.settlement"
        v-show="active === 'settlement'"
        ref="settlementRef"
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
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BalancePanel from './panels/BalancePanel.vue'
import LocationSection from './panels/basic/LocationSection.vue'
import MovementPanel from './panels/MovementPanel.vue'
import RequestPanel from './panels/RequestPanel.vue'
import SettlementPanel from './panels/SettlementPanel.vue'
import SkuSection from './panels/basic/SkuSection.vue'
import './workspace.css'

/**
 * 物料管理内容区：业务分组菜单在全局侧边栏（AdminLayout，2026-10-23 需求：
 * 基础数据 / 库存查询 / 库存业务 / 核销管理 四个分组替换原「物料工作台」入口），
 * 本页按路由参数 :panel 挂载对应面板，同记录参数切换复用组件实例
 * 面板懒挂载：首次进入才挂载（v-if），此后切回仅切换显示（v-show）保留筛选与分页状态
 */
const route = useRoute()

/** 面板 key → 标题（与侧边栏分组叶子项一一对应） */
const PANELS = {
  basic_sku: '物料档案',
  basic_location: '库位管理',
  balance: '物料存量',
  movement: '库存流水',
  request: '物料预约',
  settlement: '物料核销'
}

/** 当前面板：路由参数非法时回退物料存量（默认面板） */
const active = computed(() => (PANELS[route.params.panel] ? route.params.panel : 'balance'))

/** 已访问过的页面（懒挂载标记）：初始面板随路由就绪 */
const visited = reactive({ [active.value]: true })

const balanceRef = ref(null)
const movementRef = ref(null)
const requestRef = ref(null)
const settlementRef = ref(null)
const skuRef = ref(null)
const locationRef = ref(null)

const panelRefs = {
  balance: balanceRef,
  movement: movementRef,
  request: requestRef,
  settlement: settlementRef,
  basic_sku: skuRef,
  basic_location: locationRef
}

/** 内容区页面标题：由内容区补足当前页面名 */
const activeLabel = computed(() => PANELS[active.value])

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

.ws-main {
  margin-top: 8px;
  min-width: 0;
}

.ws-main-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--g1);
}
</style>
