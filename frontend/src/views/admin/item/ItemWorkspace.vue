<template>
  <div class="item_workspace">
    <div class="item_workspace__body">
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

.item_workspace__body {
  display: flex;
  align-items: flex-start;
  gap: 18px;
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
</style>
