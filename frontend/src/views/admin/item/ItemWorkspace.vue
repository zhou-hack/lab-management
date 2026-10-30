<template>
  <div class="item_workspace">
    <header class="item_workspace__header">
      <div>
        <h1 class="t-title item_workspace__title">
          物料管理工作台
        </h1>
        <p class="t-sub">
          物料档案、库存查询与库存业务集中管理
        </p>
      </div>
    </header>

    <div class="item_workspace__body">
      <aside class="item_workspace__aside">
        <el-menu
          :default-active="activeKey"
          class="item_workspace__menu"
          @select="selectTab"
        >
          <el-menu-item
            v-for="tab in visibleTabs"
            :key="tab.key"
            :index="tab.key"
          >
            {{ tab.label }}
          </el-menu-item>
        </el-menu>
      </aside>

      <main class="item_workspace__main">
        <template v-if="activeTab">
          <h2 class="item_workspace__panel_title">
            {{ activeTab.label }}
          </h2>
          <component
            :is="panelComponents[activeTab.key]"
            :key="activeTab.key"
          />
        </template>
        <el-empty
          v-else
          description="暂无可访问的物料管理功能"
        />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ITEM_WORKBENCH_TABS } from './workbench.config'
import { getVisibleWorkbenchTabs, resolveWorkbenchTab } from './workbench.rules'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const activeKey = ref('')

const panelComponents = Object.fromEntries(
  ITEM_WORKBENCH_TABS.map((tab) => [tab.key, defineAsyncComponent(tab.loader)])
)

const visibleTabs = computed(() => getVisibleWorkbenchTabs(store.permissions))
const activeTab = computed(() =>
  visibleTabs.value.find((tab) => tab.key === activeKey.value) || null
)

function routeWithTab(tab, replace = true) {
  const query = { ...route.query, tab }
  const navigation = replace ? router.replace : router.push
  return navigation({ path: route.path, query }).catch(() => {})
}

function selectTab(key) {
  if (!visibleTabs.value.some((tab) => tab.key === key) || key === activeKey.value) return
  activeKey.value = key
  routeWithTab(key, false)
}

watch(
  [() => route.query.tab, () => store.permissions],
  ([requestedTab]) => {
    const nextKey = resolveWorkbenchTab(requestedTab, visibleTabs.value)
    activeKey.value = nextKey
    if (nextKey && requestedTab !== nextKey) routeWithTab(nextKey)
  },
  { immediate: true }
)
</script>

<style scoped>
.item_workspace {
  max-width: 1680px;
  margin: 0 auto;
}

.item_workspace__header {
  margin-bottom: 18px;
}

.item_workspace__title {
  margin: 0 0 6px;
}

.item_workspace__header p {
  margin: 0;
}

.item_workspace__body {
  display: flex;
  align-items: flex-start;
  gap: 18px;
}

.item_workspace__aside {
  position: sticky;
  top: 20px;
  flex: 0 0 176px;
  padding: 7px 0;
  overflow: hidden;
  background: var(--surface);
  border: .5px solid var(--hair);
  border-radius: var(--r-md);
}

.item_workspace__menu {
  border-right: 0;
}

.item_workspace__menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
}

.item_workspace__main {
  flex: 1;
  min-width: 0;
}

.item_workspace__panel_title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

@media (max-width: 900px) {
  .item_workspace__body {
    display: block;
  }

  .item_workspace__aside {
    position: static;
    width: 100%;
    margin-bottom: 14px;
    padding: 0;
    overflow-x: auto;
  }

  .item_workspace__menu {
    display: flex;
    width: max-content;
    min-width: 100%;
    border-bottom: 0;
  }

  .item_workspace__menu :deep(.el-menu-item) {
    flex: 0 0 auto;
    padding: 0 18px;
    border-bottom: 2px solid transparent;
  }
}
</style>
