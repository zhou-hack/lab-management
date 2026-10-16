<template>
  <div class="home_view">
    <!-- 页头：问候 + 角色 -->
    <header class="home_view__header">
      <div>
        <h1 class="home_view__hello">
          {{ greeting }}，{{ store.user?.realName || '用户' }}
        </h1>
        <p class="t-sub">
          {{ roleText }} · {{ todayText }}
        </p>
      </div>
    </header>

    <!-- KPI 指标行：按账号权限展示真实统计 -->
    <div
      v-if="kpis.length"
      class="grid-responsive home_view__section"
    >
      <div
        v-for="kpi in kpis"
        :key="kpi.label"
        class="card home_view__stat"
      >
        <div class="icon_box">
          <el-icon :size="19">
            <component :is="kpi.icon" />
          </el-icon>
        </div>
        <div class="home_view__stat_text">
          <span
            v-if="dashboard.loading"
            class="skeleton home_view__stat_skeleton"
          />
          <span
            v-else
            class="home_view__stat_value t-num"
          >{{ kpi.value ?? '—' }}</span>
          <span class="home_view__stat_label">{{ kpi.label }}</span>
        </div>
      </div>
    </div>

    <!-- 可视化图表：设备状态环形 + 实验室设备分布（真实数据聚合，无虚构） -->
    <div
      v-if="canViewAssets"
      class="home_view__charts home_view__section"
    >
      <div class="card card--pad0 home_view__chart_card">
        <div class="card__head">
          <span class="card__title">设备状态分布</span>
          <span
            v-if="dashboard.partial"
            class="t-hint"
          >统计前 {{ dashboard.assetRows.length }} 条</span>
        </div>
        <div class="card__body">
          <div
            v-if="dashboard.loading"
            class="home_view__chart_loading"
          >
            <span class="skeleton home_view__sk_donut" />
            <div class="home_view__sk_col">
              <span class="skeleton home_view__sk_line" />
              <span class="skeleton home_view__sk_line" />
              <span class="skeleton home_view__sk_line" />
            </div>
          </div>
          <div
            v-else-if="!dashboard.assetRows.length"
            class="empty"
          >
            <div class="empty__icon">
              ∅
            </div>
            <div class="empty__text">
              暂无设备资产
            </div>
          </div>
          <div
            v-else
            class="home_view__donut_row"
          >
            <div
              class="home_view__donut"
              :style="donutStyle"
            >
              <div class="home_view__donut_hole">
                <span class="home_view__donut_total t-num">{{ dashboard.assetRows.length }}</span>
                <span class="home_view__donut_unit">台设备</span>
              </div>
            </div>
            <ul class="home_view__legend">
              <li
                v-for="seg in statusAgg"
                :key="seg.key"
              >
                <span
                  class="home_view__legend_dot"
                  :style="{ background: seg.color }"
                />
                <span class="home_view__legend_label">{{ seg.label }}</span>
                <span class="home_view__figure home_view__legend_count">{{ seg.count }}</span>
                <span class="home_view__figure home_view__legend_pct">{{ seg.pct }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <div class="card card--pad0 home_view__chart_card">
        <div class="card__head">
          <span class="card__title">实验室设备分布</span>
        </div>
        <div class="card__body">
          <div
            v-if="dashboard.loading"
            class="home_view__chart_loading"
          >
            <div class="home_view__sk_col home_view__sk_col--full">
              <span class="skeleton home_view__sk_line" />
              <span class="skeleton home_view__sk_line" />
              <span class="skeleton home_view__sk_line" />
              <span class="skeleton home_view__sk_line" />
            </div>
          </div>
          <div
            v-else-if="!labAgg.length"
            class="empty"
          >
            <div class="empty__icon">
              ∅
            </div>
            <div class="empty__text">
              暂无分布数据
            </div>
          </div>
          <div
            v-else
            class="home_view__bars"
          >
            <div
              v-for="item in labAgg"
              :key="item.name"
              class="home_view__bar_row"
            >
              <span
                class="home_view__bar_label"
                :title="item.name"
              >{{ item.name }}</span>
              <span class="home_view__bar_track">
                <span
                  class="home_view__bar_fill"
                  :style="{ width: (item.count / labMax * 100) + '%' }"
                />
              </span>
              <span class="home_view__figure home_view__bar_value">{{ item.count }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 功能入口：按当前账号权限与可用菜单动态启用 -->
    <section class="home_view__section">
      <h3 class="sechead">
        常用功能
      </h3>
      <div class="home_view__grid">
        <button
          v-for="entry in entries"
          :key="entry.name"
          class="entry card"
          :class="{ 'entry--disabled': !entry.path }"
          type="button"
          @click="go(entry)"
        >
          <span
            class="icon_box"
            :class="`icon_box--${entry.tone}`"
          >
            <el-icon><component :is="entry.icon" /></el-icon>
          </span>
          <span class="entry__text">
            <span class="entry__name">{{ entry.name }}</span>
            <span class="entry__desc">{{ entry.desc }}</span>
          </span>
        </button>
      </div>
    </section>

    <div class="home_view__columns">
      <!-- 待办：空态（接口接入前不展示虚构数据） -->
      <section class="home_view__section">
        <div class="card card--pad0">
          <div class="card__head">
            <span class="card__title">待我处理</span>
          </div>
          <div class="card__body">
            <div class="empty">
              <div class="empty__icon">
                ✓
              </div>
              <div class="empty__text">
                暂无待办事项，一切顺利
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 最近使用记录：空态 -->
      <section class="home_view__section">
        <div class="card card--pad0">
          <div class="card__head">
            <span class="card__title">最近使用记录</span>
          </div>
          <div class="card__body">
            <div class="empty">
              <div class="empty__icon">
                ∅
              </div>
              <div class="empty__text">
                暂无使用记录
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Box,
  Calendar,
  Collection,
  DataLine,
  Grid,
  Location,
  Monitor,
  Notebook,
  Tools
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { pageAssets, pageModels } from '@/api/equipment'
import { pageSpaces } from '@/api/space'
import { EQUIPMENT_STATUS } from '@/utils/enums'

const router = useRouter()
const store = useUserStore()

/* ---------- 权限门控：统计与图表仅对有权限的模块拉取 ---------- */
const PERMS = {
  space: 'space:space:list',
  model: 'equipment:model:list',
  asset: 'equipment:asset:list'
}

const hasPerm = (perm) => store.permissions.includes(perm)
const canViewAssets = computed(() => hasPerm(PERMS.asset))

/* ---------- 仪表数据：真实接口聚合；单项失败回退“—”，不虚构 ---------- */
const dashboard = reactive({
  loading: true,
  spaceTotal: null,
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
  if (hasPerm(PERMS.space)) jobs.push(fetchTotal(pageSpaces, 'spaceTotal'))
  if (hasPerm(PERMS.model)) jobs.push(fetchTotal(pageModels, 'modelTotal'))
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
  if (hasPerm(PERMS.space)) {
    list.push({ label: '场地', icon: Location, value: dashboard.spaceTotal })
  }
  if (hasPerm(PERMS.model)) {
    list.push({ label: '设备型号', icon: Grid, value: dashboard.modelTotal })
  }
  if (hasPerm(PERMS.asset)) {
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

/* ---------- 问候与日期 ---------- */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  }).format(new Date())
)

const ROLE_TEXT = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生', ASSISTANT: '教师助理', OPS_ADMIN: '运维管理员' }
const roleText = computed(() => store.roles.map((r) => ROLE_TEXT[r] || r).join(' · ') || '实验室用户')

/** 当前账号菜单树中已开放的页面路径集合（入口可用性以此为准） */
const openPaths = computed(() => {
  const paths = new Set()
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (node.menuType === 'PAGE' && node.routePath) paths.add(node.routePath)
      if (node.children && node.children.length) walk(node.children)
    }
  }
  walk(store.menus)
  return paths
})

// 已开放入口指向菜单中的真实页面；未开放模块置灰（path 为空）
const entries = computed(() => [
  {
    name: '场地预约',
    desc: '申请实验室使用时段',
    icon: Calendar,
    tone: 'ok',
    path: openPaths.value.has('/booking/labs') ? '/booking/labs' : ''
  },
  {
    name: '场地管理',
    desc: '实验室目录维护',
    icon: Location,
    tone: 'brand',
    path: openPaths.value.has('/admin/space/labs') ? '/admin/space/labs' : ''
  },
  {
    name: '设备台账',
    desc: '资产登记 · 迁移 · 定级',
    icon: Notebook,
    tone: 'brand',
    path: openPaths.value.has('/admin/equipment/asset') ? '/admin/equipment/asset' : ''
  },
  {
    name: '设备型号',
    desc: '型号码与规格参数',
    icon: Grid,
    tone: 'brand',
    path: openPaths.value.has('/admin/equipment/model') ? '/admin/equipment/model' : ''
  },
  {
    name: '设备分类',
    desc: '多级分类树维护',
    icon: Collection,
    tone: 'brand',
    path: openPaths.value.has('/admin/equipment/category') ? '/admin/equipment/category' : ''
  },
  {
    name: '物料工作台',
    desc: '库存与申领一站式',
    icon: Box,
    tone: 'warn',
    path: openPaths.value.has('/admin/item/workspace') ? '/admin/item/workspace' : ''
  },
  { name: '器材借用', desc: '浏览并提交器材预约', icon: Tools, tone: 'muted', path: '' },
  { name: '使用记录', desc: '借出归还与拍照留痕', icon: DataLine, tone: 'muted', path: '' },
  { name: '通知消息', desc: '审批与催办提醒', icon: Bell, tone: 'muted', path: '' }
].filter((entry) => entry.path || entry.tone === 'muted'))

function go(entry) {
  if (!entry.path) {
    ElMessage.info('该功能建设中，敬请期待')
    return
  }
  router.push(entry.path)
}
</script>

<style scoped>
.home_view__header {
  padding: 8px 2px 18px;
}

.home_view__hello {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.home_view__section {
  margin-bottom: 18px;
}

/* KPI 统计卡：图标容器 + 指标数字 + 标签 */
.home_view__stat {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
}

.home_view__stat_text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.home_view__stat_value {
  font-size: 28px;
  line-height: 1.15;
}

.home_view__stat_skeleton {
  width: 56px;
  height: 28px;
  border-radius: var(--r-xs);
}

.home_view__stat_label {
  font-size: 12.5px;
  color: var(--g3);
}

/* 图表区：双列卡片 */
.home_view__charts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.home_view__chart_loading {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 12px 0;
}

.home_view__sk_donut {
  width: 132px;
  height: 132px;
  border-radius: 50%;
  flex-shrink: 0;
}

.home_view__sk_col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.home_view__sk_col--full {
  padding: 10px 0;
}

.home_view__sk_line {
  height: 14px;
  border-radius: var(--r-xs);
}

/* 环形图（conic-gradient 语义分色）+ 图例 */
.home_view__donut_row {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 8px 0 4px;
}

.home_view__donut {
  width: 148px;
  height: 148px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.home_view__donut_hole {
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

.home_view__donut_total {
  font-size: 26px;
  line-height: 1.1;
}

.home_view__donut_unit {
  font-size: 12px;
  color: var(--g3);
}

.home_view__legend {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.home_view__legend li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.home_view__legend_dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.home_view__legend_label {
  font-size: 13px;
  color: var(--g2);
}

.home_view__legend_count {
  margin-left: auto;
  font-size: 13px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.home_view__legend_pct {
  width: 40px;
  text-align: right;
  font-size: 12px;
  color: var(--g3);
}

/* 条形排行：实验室保有量 */
.home_view__bars {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 6px 0 2px;
}

.home_view__bar_row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.home_view__bar_label {
  width: 88px;
  font-size: 13px;
  color: var(--g2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
}

.home_view__bar_track {
  flex: 1;
  height: 10px;
  border-radius: var(--r-full);
  background: var(--surface-sunken);
  overflow: hidden;
}

.home_view__bar_fill {
  display: block;
  height: 100%;
  border-radius: var(--r-full);
  background: var(--brand);
  transition: width var(--dur-slow) var(--ease);
}

.home_view__bar_value {
  width: 28px;
  text-align: right;
  font-size: 13px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

/* 数字等宽：图例计数 / 占比 / 条形值 */
.home_view__figure {
  font-variant-numeric: tabular-nums;
}

/* 功能入口：桌面三列网格，卡片悬浮描边变蓝 */
.home_view__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.entry {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
}

.entry:not(.entry--disabled):hover {
  border-color: var(--hair-brand);
  transform: translateY(-2px);
}

.entry:not(.entry--disabled):active {
  transform: scale(.98);
}

.entry--disabled {
  opacity: .55;
  cursor: default;
}

.entry__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.entry__name {
  font-size: 14.5px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.entry__desc {
  font-size: 12px;
  color: var(--g3);
}

/* 下半区双列 */
.home_view__columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.card--pad0 {
  padding: 0;
}

.card--pad0 .card__body {
  padding: 4px 18px 8px;
}

.home_view__chart_card .card__body {
  padding: 14px 20px 16px;
}

@media (max-width: 900px) {
  .home_view__charts { grid-template-columns: 1fr; }
  .home_view__grid { grid-template-columns: repeat(2, 1fr); }
  .home_view__columns { grid-template-columns: 1fr; }
  .home_view__donut_row { flex-direction: column; }
}
</style>
