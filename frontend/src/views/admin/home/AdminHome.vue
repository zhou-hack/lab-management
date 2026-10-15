<template>
  <div class="page admin_home">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          {{ greeting }}，{{ store.userInfo?.realName || '管理员' }}
        </h2>
        <p class="t-sub">
          {{ dateLine }} · 实验室设备管理系统
        </p>
      </div>
    </div>

    <!-- KPI 指标行：按账号权限展示真实统计（规范第 4 节指标数字） -->
    <div
      v-if="kpis.length"
      class="grid-responsive"
    >
      <div
        v-for="kpi in kpis"
        :key="kpi.label"
        class="card admin_home__stat"
      >
        <div class="icon_box">
          <el-icon :size="19">
            <component :is="kpi.icon" />
          </el-icon>
        </div>
        <div class="admin_home__stat_text">
          <span
            v-if="dashboard.loading"
            class="skeleton admin_home__stat_skeleton"
          />
          <span
            v-else
            class="admin_home__stat_value t-num"
          >{{ kpi.value ?? '—' }}</span>
          <span class="admin_home__stat_label">{{ kpi.label }}</span>
        </div>
      </div>
    </div>

    <!-- 可视化图表：设备状态环形 + 实验室设备分布（真实数据聚合，无虚构） -->
    <div
      v-if="canViewAssets"
      class="admin_home__charts"
    >
      <div class="card admin_home__chart_card">
        <div class="sechead">
          设备状态分布
          <span
            v-if="dashboard.partial"
            class="t-hint"
          >统计前 {{ dashboard.assetRows.length }} 条</span>
        </div>
        <div
          v-if="dashboard.loading"
          class="admin_home__chart_loading"
        >
          <span class="skeleton admin_home__sk_donut" />
          <div class="admin_home__sk_col">
            <span class="skeleton admin_home__sk_line" />
            <span class="skeleton admin_home__sk_line" />
            <span class="skeleton admin_home__sk_line" />
          </div>
        </div>
        <div
          v-else-if="!dashboard.assetRows.length"
          class="empty"
        >
          <el-icon
            class="empty__icon"
            :size="40"
          >
            <Monitor />
          </el-icon>
          暂无设备资产
        </div>
        <div
          v-else
          class="admin_home__donut_row"
        >
          <div
            class="admin_home__donut"
            :style="donutStyle"
          >
            <div class="admin_home__donut_hole">
              <span class="admin_home__donut_total t-num">{{ dashboard.assetRows.length }}</span>
              <span class="admin_home__donut_unit">台设备</span>
            </div>
          </div>
          <ul class="admin_home__legend">
            <li
              v-for="seg in statusAgg"
              :key="seg.key"
            >
              <span
                class="admin_home__legend_dot"
                :style="{ background: seg.color }"
              />
              <span class="admin_home__legend_label">{{ seg.label }}</span>
              <span class="admin_home__figure admin_home__legend_count">{{ seg.count }}</span>
              <span class="admin_home__figure admin_home__legend_pct">{{ seg.pct }}</span>
            </li>
          </ul>
        </div>
      </div>

      <div class="card admin_home__chart_card">
        <div class="sechead">
          实验室设备分布
        </div>
        <div
          v-if="dashboard.loading"
          class="admin_home__chart_loading"
        >
          <div class="admin_home__sk_col admin_home__sk_col--full">
            <span class="skeleton admin_home__sk_line" />
            <span class="skeleton admin_home__sk_line" />
            <span class="skeleton admin_home__sk_line" />
            <span class="skeleton admin_home__sk_line" />
          </div>
        </div>
        <div
          v-else-if="!labAgg.length"
          class="empty"
        >
          <el-icon
            class="empty__icon"
            :size="40"
          >
            <Location />
          </el-icon>
          暂无分布数据
        </div>
        <div
          v-else
          class="admin_home__bars"
        >
          <div
            v-for="item in labAgg"
            :key="item.name"
            class="admin_home__bar_row"
          >
            <span
              class="admin_home__bar_label"
              :title="item.name"
            >{{ item.name }}</span>
            <span class="admin_home__bar_track">
              <span
                class="admin_home__bar_fill"
                :style="{ width: (item.count / labMax * 100) + '%' }"
              />
            </span>
            <span class="admin_home__figure admin_home__bar_value">{{ item.count }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 功能入口：三列桌面网格（规范 7.5） -->
    <div class="admin_home__grid">
      <div
        v-for="entry in visibleEntries"
        :key="entry.path"
        class="card card--hover admin_home__entry"
        tabindex="0"
        @click="router.push(entry.path)"
        @keyup.enter="router.push(entry.path)"
      >
        <div class="icon_box">
          <el-icon :size="19">
            <component :is="entry.icon" />
          </el-icon>
        </div>
        <div class="admin_home__entry_text">
          <span class="admin_home__entry_title">{{ entry.title }}</span>
          <span class="admin_home__entry_sub">{{ entry.sub }}</span>
        </div>
      </div>
    </div>

    <!-- 待办 / 记录：双列卡，空态优先（规范 7.8：接口未接入展示真实空态） -->
    <div class="admin_home__columns">
      <div class="card">
        <div class="sechead">
          待办事项
        </div>
        <div class="empty">
          <el-icon
            class="empty__icon"
            :size="40"
          >
            <Bell />
          </el-icon>
          暂无待办事项
        </div>
      </div>
      <div class="card">
        <div class="sechead">
          操作记录
        </div>
        <div class="empty">
          <el-icon
            class="empty__icon"
            :size="40"
          >
            <Document />
          </el-icon>
          暂无操作记录
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bell,
  Collection,
  Document,
  Grid,
  Location,
  Monitor,
  Notebook,
  OfficeBuilding
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { pageAssets, pageModels } from '@/api/equipment'
import { pageCampuses, pageLabs } from '@/api/space'
import { EQUIPMENT_STATUS } from '@/utils/enums'

const router = useRouter()
const store = useUserStore()

/* ---------- 权限门控：统计与图表仅对有权限的模块拉取 ---------- */
const PERMS = {
  campus: 'space:campus:list',
  lab: 'space:lab:list',
  model: 'equipment:model:list',
  asset: 'equipment:asset:list'
}

const canViewAssets = computed(() => store.perms.includes(PERMS.asset))

/* ---------- 仪表数据：真实接口聚合；单项失败回退“—”，不虚构 ---------- */
const dashboard = reactive({
  loading: true,
  campusTotal: null,
  labTotal: null,
  modelTotal: null,
  assetTotal: null,
  assetRows: [],
  partial: false
})

async function loadDashboard() {
  const jobs = []
  const fetchTotal = (api, key) =>
    api({ pageNum: 1, pageSize: 1 })
      .then((r) => { dashboard[key] = r.total })
      .catch(() => {})
  if (store.perms.includes(PERMS.campus)) jobs.push(fetchTotal(pageCampuses, 'campusTotal'))
  if (store.perms.includes(PERMS.lab)) jobs.push(fetchTotal(pageLabs, 'labTotal'))
  if (store.perms.includes(PERMS.model)) jobs.push(fetchTotal(pageModels, 'modelTotal'))
  if (canViewAssets.value) {
    jobs.push(
      pageAssets({ pageNum: 1, pageSize: 100 })
        .then((r) => {
          dashboard.assetTotal = r.total
          dashboard.assetRows = r.records || []
          dashboard.partial = r.total > dashboard.assetRows.length
        })
        .catch(() => {})
    )
  }
  await Promise.all(jobs)
  dashboard.loading = false
}
onMounted(loadDashboard)

const kpis = computed(() => {
  const list = []
  if (store.perms.includes(PERMS.campus)) {
    list.push({ label: '校区', icon: OfficeBuilding, value: dashboard.campusTotal })
  }
  if (store.perms.includes(PERMS.lab)) {
    list.push({ label: '实验室', icon: Location, value: dashboard.labTotal })
  }
  if (store.perms.includes(PERMS.model)) {
    list.push({ label: '设备型号', icon: Grid, value: dashboard.modelTotal })
  }
  if (store.perms.includes(PERMS.asset)) {
    list.push({ label: '设备资产', icon: Monitor, value: dashboard.assetTotal })
  }
  return list
})

/* 状态环形图：语义色只出现在语义状态（空闲绿 / 维护橙 / 报废灰） */
const STATUS_COLORS = {
  IDLE: 'var(--ok)',
  MAINTAINING: 'var(--warn)',
  SCRAPPED: 'var(--g4)'
}
const STATUS_ORDER = ['IDLE', 'MAINTAINING', 'SCRAPPED']

const statusAgg = computed(() => {
  const counts = new Map()
  for (const row of dashboard.assetRows) {
    const key = row.status || 'UNKNOWN'
    counts.set(key, (counts.get(key) || 0) + 1)
  }
  const total = dashboard.assetRows.length
  return [...counts.entries()]
    .map(([key, count]) => ({
      key,
      count,
      label: EQUIPMENT_STATUS[key]?.label || '未登记',
      color: STATUS_COLORS[key] || 'var(--brand)',
      pct: total ? Math.round((count / total) * 100) + '%' : '0%'
    }))
    .sort((a, b) => {
      const ai = STATUS_ORDER.indexOf(a.key)
      const bi = STATUS_ORDER.indexOf(b.key)
      return (ai === -1 ? STATUS_ORDER.length : ai) - (bi === -1 ? STATUS_ORDER.length : bi)
    })
})

const donutStyle = computed(() => {
  const total = dashboard.assetRows.length
  if (!total) return {}
  let acc = 0
  const stops = []
  for (const seg of statusAgg.value) {
    const from = (acc / total) * 360
    acc += seg.count
    const to = (acc / total) * 360
    if (to > from) stops.push(`${seg.color} ${from}deg ${to}deg`)
  }
  return { background: stops.length ? `conic-gradient(${stops.join(', ')})` : 'var(--surface-sunken)' }
})

/* 实验室设备分布：取保有量前六的实验室 */
const labAgg = computed(() => {
  const map = new Map()
  for (const row of dashboard.assetRows) {
    const name = row.labName || '未指定实验室'
    map.set(name, (map.get(name) || 0) + 1)
  }
  return [...map.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 6)
})

const labMax = computed(() => Math.max(1, ...labAgg.value.map((i) => i.count)))

/* ---------- 功能入口：仅展示当前账号有列表权限的模块 ---------- */
const ENTRIES = [
  {
    path: '/admin/space',
    title: '空间管理',
    sub: '校区 · 楼栋 · 实验室',
    icon: OfficeBuilding,
    perms: ['space:campus:list', 'space:lab:list']
  },
  {
    path: '/admin/equipment/category',
    title: '设备分类',
    sub: '多级分类树维护',
    icon: Collection,
    perms: ['equipment:category:list']
  },
  {
    path: '/admin/equipment/model',
    title: '设备型号',
    sub: '型号码与规格参数',
    icon: Grid,
    perms: ['equipment:model:list']
  },
  {
    path: '/admin/equipment/asset',
    title: '设备台账',
    sub: '资产登记 · 迁移 · 定级',
    icon: Notebook,
    perms: ['equipment:asset:list']
  }
]

const visibleEntries = computed(() =>
  ENTRIES.filter((entry) => entry.perms.some((perm) => store.perms.includes(perm)))
)

/* 问候语按时段，日期按本地日历展示 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const dateLine = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date())
</script>

<style scoped>
/* KPI 统计卡：图标容器 + 指标数字（28px 等宽）+ 标签 */
.admin_home__stat {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
}

.admin_home__stat_text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.admin_home__stat_value {
  font-size: 28px;
  line-height: 1.15;
}

.admin_home__stat_skeleton {
  width: 56px;
  height: 28px;
  border-radius: var(--r-xs);
}

.admin_home__stat_label {
  font-size: 12.5px;
  color: var(--g3);
}

/* 图表区：双列卡片 */
.admin_home__charts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.admin_home__chart_loading {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 12px 0;
}

.admin_home__sk_donut {
  width: 132px;
  height: 132px;
  border-radius: 50%;
  flex-shrink: 0;
}

.admin_home__sk_col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin_home__sk_col--full {
  padding: 10px 0;
}

.admin_home__sk_line {
  height: 14px;
  border-radius: var(--r-xs);
}

/* 环形图（conic-gradient 语义分色）+ 图例 */
.admin_home__donut_row {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 8px 0 4px;
}

.admin_home__donut {
  width: 148px;
  height: 148px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.admin_home__donut_hole {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: var(--surface);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

.admin_home__donut_total {
  font-size: 26px;
  line-height: 1.1;
}

.admin_home__donut_unit {
  font-size: 12px;
  color: var(--g3);
}

.admin_home__legend {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin_home__legend li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.admin_home__legend_dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.admin_home__legend_label {
  font-size: 13px;
  color: var(--g2);
}

.admin_home__legend_count {
  margin-left: auto;
  font-size: 13px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.admin_home__legend_pct {
  width: 40px;
  text-align: right;
  font-size: 12px;
  color: var(--g3);
}

/* 条形排行：实验室保有量 */
.admin_home__bars {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 6px 0 2px;
}

.admin_home__bar_row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin_home__bar_label {
  width: 88px;
  font-size: 13px;
  color: var(--g2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.admin_home__bar_track {
  flex: 1;
  height: 10px;
  border-radius: var(--r-full);
  background: var(--surface-sunken);
  overflow: hidden;
}

.admin_home__bar_fill {
  display: block;
  height: 100%;
  border-radius: var(--r-full);
  background: var(--brand);
  transition: width var(--dur-slow) var(--ease);
}

.admin_home__bar_value {
  width: 28px;
  text-align: right;
  font-size: 13px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

/* 数字等宽：图例计数 / 占比 / 条形值（规范第 4 节 tabular-nums） */
.admin_home__figure {
  font-variant-numeric: tabular-nums;
}

/* 功能入口：三列桌面网格，≤900px 收两列（规范 7.5） */
.admin_home__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.admin_home__entry {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.admin_home__entry:active {
  transform: scale(.98);
}

.admin_home__entry_text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.admin_home__entry_title {
  font-size: 14.5px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.admin_home__entry_sub {
  font-size: 12px;
  color: var(--g3);
}

/* 待办/记录双列，窄屏收单列 */
.admin_home__columns {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

@media (max-width: 900px) {
  .admin_home__charts {
    grid-template-columns: 1fr;
  }

  .admin_home__grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .admin_home__columns {
    grid-template-columns: 1fr;
  }

  .admin_home__donut_row {
    flex-direction: column;
  }
}
</style>
