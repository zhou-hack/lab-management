<template>
  <div class="admin_home">
    <!-- 页头：标题 + 日期 + 快捷操作 -->
    <div class="admin_home__pagehead">
      <div>
        <h2 class="admin_home__title">
          {{ greeting }}，{{ store.user?.realName || '管理员' }}
        </h2>
        <p class="t-sub">
          {{ todayText }} · 实验室设备运行概览
        </p>
      </div>
      <div class="admin_home__actions">
        <span class="admin_home__stamp t-hint">数据更新于 {{ updatedAt }}</span>
        <el-button
          :loading="loading"
          @click="refresh"
        >
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- KPI 指标卡一行 -->
    <section class="kpi_grid">
      <template v-if="loading">
        <div
          v-for="n in 4"
          :key="n"
          class="card kpi"
        >
          <div class="skeleton kpi__skeleton" />
        </div>
      </template>
      <template v-else>
        <div
          v-for="kpi in kpis"
          :key="kpi.label"
          class="card card--hover kpi glow"
        >
          <div class="kpi__top">
            <span
              class="icon_box"
              :class="`icon_box--${kpi.tone}`"
            >
              <el-icon><component :is="kpi.icon" /></el-icon>
            </span>
            <span
              class="pill"
              :class="`pill--${kpi.tone}`"
            >
              {{ kpi.tag }}
            </span>
          </div>
          <div class="kpi__num t-num">
            {{ kpi.value }}<span class="t-num__unit">{{ kpi.unit }}</span>
          </div>
          <div class="kpi__label">
            {{ kpi.label }}
          </div>
        </div>
      </template>
    </section>

    <!-- 主内容：待办列表（2 栏）+ 异常清单（1 栏） -->
    <section class="admin_home__grid">
      <div class="card card--pad0 admin_home__todos">
        <div class="card__head">
          <span class="card__title">待我审批</span>
          <span class="admin_home__headact">全部 <el-icon><ArrowRight /></el-icon></span>
        </div>
        <div class="card__body">
          <template v-if="loading">
            <div
              v-for="n in 4"
              :key="n"
              class="skeleton skeleton__row"
            />
          </template>
          <template v-else-if="approvals.length">
            <div
              v-for="item in approvals"
              :key="item.id"
              class="list_row"
            >
              <span class="icon_box icon_box--grad icon_box--lg">{{ item.tag }}</span>
              <div class="list_row__main">
                <div class="list_row__title">
                  {{ item.title }}
                </div>
                <div class="list_row__meta">
                  {{ item.applicant }}<span class="dot">·</span>{{ item.meta }}
                </div>
              </div>
              <span
                class="pill"
                :class="`pill--${item.tone}`"
              >
                {{ item.status }}
              </span>
            </div>
          </template>
          <div
            v-else
            class="empty"
          >
            <div class="empty__icon">
              ✓
            </div>
            <div class="empty__text">
              暂无待审批事项
            </div>
          </div>
        </div>
      </div>

      <div class="card card--pad0">
        <div class="card__head">
          <span class="card__title">异常与预警</span>
          <span class="pill pill--danger">{{ alerts.length }} 项</span>
        </div>
        <div class="card__body">
          <template v-if="loading">
            <div
              v-for="n in 3"
              :key="n"
              class="skeleton skeleton__row"
            />
          </template>
          <template v-else-if="alerts.length">
            <div
              v-for="alert in alerts"
              :key="alert.id"
              class="list_row"
            >
              <span
                class="icon_box icon_box--lg"
                :class="`icon_box--${alert.tone}`"
              >
                <el-icon><component :is="alert.icon" /></el-icon>
              </span>
              <div class="list_row__main">
                <div class="list_row__title list_row__title--sm">
                  {{ alert.title }}
                </div>
                <div class="list_row__meta">
                  {{ alert.meta }}
                </div>
              </div>
            </div>
          </template>
          <div
            v-else
            class="empty"
          >
            <div class="empty__icon">
              ✓
            </div>
            <div class="empty__text">
              无异常，运行良好
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 底部：近期动态流向 -->
    <section class="card card--pad0 admin_home__flow">
      <div class="card__head">
        <span class="card__title">近期动态</span>
        <span class="admin_home__headact">查看日志 <el-icon><ArrowRight /></el-icon></span>
      </div>
      <div class="card__body">
        <div
          v-for="flow in flows"
          :key="flow.id"
          class="flow__row"
        >
          <span
            class="flow__dot"
            :class="`flow__dot--${flow.tone}`"
          />
          <span class="flow__text">{{ flow.text }}</span>
          <span class="flow__time t-hint">{{ flow.time }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  ArrowRight,
  Bell,
  Box,
  Calendar,
  Refresh,
  Tools,
  Warning
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const store = useUserStore()

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

const updatedAt = ref('16:05')

const loading = ref(true)
const kpis = ref([])
const approvals = ref([])
const alerts = ref([])

const flows = [
  { id: 1, tone: 'brand', text: '李老师 提交场地预约「A3-302 · 周四 3-4 节」', time: '12 分钟前' },
  { id: 2, tone: 'ok', text: '王同学 归还数字示波器，外观完好', time: '48 分钟前' },
  { id: 3, tone: 'warn', text: '函数信号发生器 归还时登记损坏，已置维修中', time: '2 小时前' },
  { id: 4, tone: 'muted', text: '系统完成每小时超期未还扫描，无新增超期单', time: '3 小时前' }
]

function refresh() {
  loading.value = true
  setTimeout(loadMock, 420)
}

function loadMock() {
  kpis.value = [
    { label: '待审批单据', value: 6, unit: '张', tag: '需处理', tone: 'warn', icon: Bell },
    { label: '在场使用中', value: 12, unit: '台', tag: '正常', tone: 'brand', icon: Tools },
    { label: '场地今日场次', value: 9, unit: '场', tag: '进行中', tone: 'ok', icon: Calendar },
    { label: '库存预警物料', value: 3, unit: '种', tag: '待补货', tone: 'danger', icon: Box }
  ]
  approvals.value = [
    {
      id: 1,
      tag: '器',
      title: '数字示波器 · 借用申请（贵重器材）',
      applicant: '王同学',
      meta: 'A3-302 · 10-15 09:00–11:00',
      status: '待管理员审批',
      tone: 'warn'
    },
    {
      id: 2,
      tag: '场',
      title: '场地预约 · A2-105',
      applicant: '李老师',
      meta: '周四 3-4 节 · 45 人',
      status: '待审批',
      tone: 'warn'
    },
    {
      id: 3,
      tag: '器',
      title: '万用表 ×2 · 借用申请',
      applicant: '张同学',
      meta: 'A3-302 · 10-15 14:00–16:00',
      status: '老师已批',
      tone: 'brand'
    },
    {
      id: 4,
      tag: '报',
      title: '函数信号发生器 · 报废申请',
      applicant: '设备处',
      meta: '已附技术鉴定意见',
      status: '待审批',
      tone: 'warn'
    }
  ]
  alerts.value = [
    { id: 1, tone: 'danger', icon: Warning, title: '2 台器材长期维修中未处理', meta: '已超过 7 天，需线下安排维修' },
    { id: 2, tone: 'warn', icon: Box, title: '焊锡丝 库存低于安全线', meta: '当前 4 卷 / 安全线 10 卷' },
    { id: 3, tone: 'danger', icon: Bell, title: '1 张借用单超期未还 24 小时', meta: '已升级提醒申请人及管理员' }
  ]
  loading.value = false
}

onMounted(loadMock)
</script>

<style scoped>
.admin_home {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin_home__pagehead {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.admin_home__title {
  margin: 0;
  font-size: 22px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}
.admin_home__pagehead .t-sub {
  margin: 4px 0 0;
}
.admin_home__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.admin_home__stamp {
  white-space: nowrap;
}

/* KPI 网格 */
.kpi_grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.kpi {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.kpi__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kpi__num {
  font-size: 30px;
}
.kpi__label {
  font-size: 13px;
  color: var(--g3);
}
.kpi__skeleton {
  height: 96px;
}

/* 待办 + 异常 两栏 */
.admin_home__grid {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 14px;
  align-items: start;
}

.card--pad0 {
  padding: 0;
}
.card--pad0 .card__body {
  padding: 4px 20px 10px;
}
.admin_home__headact {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12.5px;
  color: var(--g3);
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease);
}
.admin_home__headact:hover {
  color: var(--brand);
}
.list_row__title--sm {
  font-size: 14px;
}
.skeleton__row {
  height: 46px;
  margin: 8px 0;
}

/* 近期动态 */
.flow__row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 2px;
  border-top: .5px solid var(--hair);
}
.flow__row:first-child {
  border-top: none;
}
.flow__dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--brand);
}
.flow__dot--ok { background: var(--ok); }
.flow__dot--warn { background: var(--warn); }
.flow__dot--danger { background: var(--danger); }
.flow__dot--muted { background: var(--g4); }
.flow__text {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  color: var(--g2);
}
.flow__time {
  flex-shrink: 0;
}

/* 响应式：窄屏依次收为两栏、单栏 */
@media (max-width: 1280px) {
  .kpi_grid { grid-template-columns: repeat(2, 1fr); }
  .admin_home__grid { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .kpi_grid { grid-template-columns: 1fr; }
}
</style>
