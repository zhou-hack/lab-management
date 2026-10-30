<template>
  <div class="page equipment_manage">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          设备管理
        </h2>
        <p class="t-sub">
          型号按分类分组（分类目录已并入型号面板）· 台账按设备型号分组；默认收起，点击面板头部展开
        </p>
      </div>
      <el-button
        class="equipment_manage__toggle_all"
        size="small"
        @click="toggleAll"
      >
        {{ allOpen ? '全部收起' : '全部展开' }}
      </el-button>
    </div>

    <el-collapse
      v-model="activePanels"
      class="equipment_manage__collapse"
    >
      <el-collapse-item
        v-for="panel in panels"
        :key="panel.key"
        :name="panel.key"
        :data-panel="panel.key"
      >
        <template #title>
          <div class="equipment_manage__panel_head">
            <span
              class="equipment_manage__panel_icon"
              :class="`equipment_manage__panel_icon--${panel.tone}`"
            >
              <el-icon :size="16">
                <component :is="panel.icon" />
              </el-icon>
            </span>
            <div class="equipment_manage__panel_text">
              <span class="equipment_manage__panel_title">{{ panel.title }}</span>
              <span class="equipment_manage__panel_sub">{{ panel.sub }}</span>
            </div>
            <span class="equipment_manage__panel_count">
              {{ counts[panel.key] ?? '—' }}{{ panel.unit }}
            </span>
          </div>
        </template>

        <ModelSection
          v-if="panel.key === 'model'"
          :active="activePanels.includes('model')"
          @count="(n) => (counts.model = n)"
        />
        <AssetSection
          v-else-if="panel.key === 'asset'"
          :active="activePanels.includes('asset')"
          @count="(n) => (counts.asset = n)"
        />
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Goods, Notebook } from '@element-plus/icons-vue'
import ModelSection from '@/views/admin/equipment/ModelManage.vue'
import AssetSection from '@/views/admin/equipment/AssetManage.vue'

/**
 * 设备管理整合页：设备管理（型号）/ 设备台账两个折叠面板（规范 7.4 统一布局内）。
 * - 分类目录已并入型号面板的「管理分类」抽屉，不再单独成页
 * - 型号按所属分类分组折叠，台账按设备型号分组折叠；面板可各自展开收起、允许同时展开，
 *   收起时子组件 DOM 保留，查询条件与分页状态不丢失
 * - 侧栏单菜单 /admin/equipment 直达本页，面板默认收起，点击展开
 * - 子组件首次展开才请求数据（懒加载），计数由子组件上报展示于面板头
 */

const panels = [
  {
    key: 'model',
    title: '设备管理',
    sub: '按分类分组 · 型号码 · 规格参数 · 归还擦除 · 分类目录管理',
    unit: ' 个型号',
    icon: Goods,
    tone: 'ok'
  },
  {
    key: 'asset',
    title: '设备台账',
    sub: '按设备型号分组 · 资产登记 · 迁移 · 价值定级',
    unit: ' 台资产',
    icon: Notebook,
    tone: 'warn'
  }
]

/** 面板计数（子组件上报；null 表示未加载） */
const counts = reactive({ model: null, asset: null })

/** 单页承载型号与台账，默认收起，点开才展开（懒加载由子组件 active 驱动） */
const activePanels = ref([])

const allOpen = computed(() => activePanels.value.length === panels.length)

function toggleAll() {
  activePanels.value = allOpen.value ? [] : panels.map((p) => p.key)
}
</script>

<style scoped>
/* 页头右侧按钮与标题底部对齐 */
.equipment_manage__toggle_all {
  flex-shrink: 0;
}

/* ---------- 折叠面板卡片化（规范 7.1：大圆角 + 发丝线 + 无阴影） ---------- */
.equipment_manage__collapse {
  --el-collapse-border-color: var(--hair);
  border-top: none;
  border-bottom: none;
}

.equipment_manage__collapse :deep(.el-collapse-item) {
  background: var(--surface);
  border: .5px solid var(--hair);
  border-radius: var(--r-lg);
  margin-bottom: 12px;
  overflow: hidden;
  transition: border-color var(--dur) var(--ease);
}

.equipment_manage__collapse :deep(.el-collapse-item:hover) {
  border-color: var(--hair-brand);
}

.equipment_manage__collapse :deep(.el-collapse-item.is-active) {
  border-color: var(--hair-brand);
}

.equipment_manage__collapse :deep(.el-collapse-item__wrap) {
  background: transparent;
  border-bottom: none;
}

.equipment_manage__collapse :deep(.el-collapse-item__header) {
  height: 60px;
  padding: 0 22px;
  border-bottom: none;
  line-height: normal;
  transition: background-color var(--dur-fast) var(--ease);
}

.equipment_manage__collapse :deep(.el-collapse-item__header:hover) {
  background: var(--brand-a04);
}

.equipment_manage__collapse :deep(.el-collapse-item.is-active .el-collapse-item__header) {
  border-bottom: .5px solid var(--hair);
}

.equipment_manage__collapse :deep(.el-collapse-item__arrow) {
  color: var(--g3);
  font-size: 14px;
}

.equipment_manage__collapse :deep(.el-collapse-item.is-active .el-collapse-item__arrow) {
  color: var(--brand);
}

.equipment_manage__collapse :deep(.el-collapse-item__content) {
  padding: 18px 22px;
}

/* ---------- 面板头：图标容器 + 标题/副标题 + 计数 ---------- */
.equipment_manage__panel_head {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding-right: 6px;
}

.equipment_manage__panel_icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 11px;
  flex-shrink: 0;
}

.equipment_manage__panel_icon--ok {
  background: var(--ok-bg);
  color: var(--ok);
}

.equipment_manage__panel_icon--warn {
  background: var(--warn-bg);
  color: var(--warn);
}

.equipment_manage__panel_text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.equipment_manage__panel_title {
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color var(--dur) var(--ease);
}

.equipment_manage__collapse :deep(.el-collapse-item.is-active) .equipment_manage__panel_title {
  color: var(--brand);
}

.equipment_manage__panel_sub {
  font-size: 12px;
  color: var(--g3);
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.equipment_manage__panel_count {
  font-size: 12px;
  color: var(--g3);
  white-space: nowrap;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
</style>
