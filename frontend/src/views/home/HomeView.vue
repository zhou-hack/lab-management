<template>
  <div class="home_view">
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

    <section class="home_view__section">
      <h3 class="sechead">
        常用功能
      </h3>
      <div class="home_view__grid">
        <button
          v-for="entry in entries"
          :key="entry.name"
          class="entry card"
          :class="{ 'entry--disabled': !entry.path && !entry.action }"
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
      <section class="home_view__section">
        <div class="card card--pad0">
          <div class="card__head">
            <span class="card__title">待我处理</span>
          </div>
          <div class="card__body">
            <el-skeleton
              v-if="homeLists.pendingLoading"
              :rows="3"
              animated
            />
            <div
              v-else-if="homeLists.pending.length"
              class="home_list"
            >
              <button
                v-for="item in homeLists.pending"
                :key="item.id"
                type="button"
                class="home_list__item"
                @click="router.push(item.route)"
              >
                <span class="home_list__content">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.meta }}</span>
                </span>
                <time>{{ formatInstant(item.time) }}</time>
              </button>
            </div>
            <div
              v-else
              class="empty"
            >
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

      <section class="home_view__section">
        <div class="card card--pad0">
          <div class="card__head">
            <span class="card__title">最近使用记录</span>
          </div>
          <div class="card__body">
            <el-skeleton
              v-if="homeLists.recentLoading"
              :rows="3"
              animated
            />
            <div
              v-else-if="homeLists.recent.length"
              class="home_list"
            >
              <button
                v-for="item in homeLists.recent"
                :key="item.id"
                type="button"
                class="home_list__item"
                @click="router.push(item.route)"
              >
                <span class="home_list__content">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.meta }}</span>
                </span>
                <time>{{ formatInstant(item.time) }}</time>
              </button>
            </div>
            <div
              v-else
              class="empty"
            >
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
import { Bell, Box, Calendar, Document, Location } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useNotificationStore } from '@/store/notification'
import { pageSpaces } from '@/api/space'
import { pageAllBookings, pageMyBookings } from '@/api/booking'
import { pageAdminClaims, pageDocuments } from '@/api/item/workflow'
import { pageMyClaims } from '@/api/item/claim'
import { pagePickups } from '@/api/item/pickup'
import { formatInstant } from '@/utils/datetime'
import {
  latestItems,
  mapBookingTodo,
  mapBookingUsage,
  mapClaimTodo,
  mapClaimUsage,
  mapDocumentUsage,
  mapPickupTodo
} from './home.rules'

const router = useRouter()
const store = useUserStore()
const notification = useNotificationStore()
const SPACE_LIST = 'space:space:list'
const hasPerm = (perm) => store.permissions.includes(perm)

const dashboard = reactive({
  loading: true,
  spaceTotal: null
})
const homeLists = reactive({
  pending: [],
  recent: [],
  pendingLoading: false,
  recentLoading: false
})

function recordsOf(result) {
  return result?.records || []
}

function settledItems(results) {
  return results.flatMap((result) => {
    if (result.status !== 'fulfilled') return []
    const { kind, data } = result.value
    const records = recordsOf(data)
    if (kind === 'claimTodo') return records.map(mapClaimTodo)
    if (kind === 'bookingTodo') return records.map(mapBookingTodo)
    if (kind === 'documentUsage') return records.map(mapDocumentUsage)
    if (kind === 'bookingUsage') return records.map((item) => mapBookingUsage(item, true))
    if (kind === 'ownBookingUsage') return records.map((item) => mapBookingUsage(item, false))
    if (kind === 'claimUsage') return records.map(mapClaimUsage)
    if (kind === 'pickupTodo') return records.map(mapPickupTodo)
    return []
  })
}

async function loadPendingItems() {
  const requests = []
  if (hasPerm('booking:lab:approve')) {
    requests.push(pageAllBookings({ status: 'PENDING', pageNum: 1, pageSize: 5 })
      .then((data) => ({ kind: 'bookingTodo', data })))
  }
  if (hasPerm('item:claim:review')) {
    requests.push(pageAdminClaims({
      status: 'PENDING', managedOnly: true, pageNum: 1, pageSize: 5,
      sortBy: 'createTime', sortOrder: 'desc'
    }).then((data) => ({ kind: 'claimTodo', data })))
  }
  if (hasPerm('item:pickup:list')) {
    requests.push(pagePickups({ pageNum: 1, pageSize: 5 })
      .then((data) => ({ kind: 'pickupTodo', data })))
  }
  if (!requests.length) return
  homeLists.pendingLoading = true
  try {
    homeLists.pending = latestItems(settledItems(await Promise.allSettled(requests)))
  } finally {
    homeLists.pendingLoading = false
  }
}

async function loadRecentItems() {
  const requests = []
  if (hasPerm('booking:lab:approve')) {
    requests.push(pageAllBookings({ status: 'APPROVED', pageNum: 1, pageSize: 100 })
      .then((data) => ({ kind: 'bookingUsage', data })))
  } else if (hasPerm('booking:lab:list')) {
    requests.push(pageMyBookings({ status: 'APPROVED', pageNum: 1, pageSize: 100 })
      .then((data) => ({ kind: 'ownBookingUsage', data })))
  }
  if (hasPerm('item:document:list')) {
    requests.push(pageDocuments({
      businessScene: 'CLAIM_ISSUE', pageNum: 1, pageSize: 5,
      sortBy: 'occurredAt', sortOrder: 'desc'
    }).then((data) => ({ kind: 'documentUsage', data })))
  } else if (hasPerm('item:claim:list')) {
    requests.push(pageMyClaims({ pageNum: 1, pageSize: 5, sortBy: 'createTime', sortOrder: 'desc' })
      .then((data) => ({ kind: 'claimUsage', data })))
  }
  if (!requests.length) return
  homeLists.recentLoading = true
  try {
    homeLists.recent = latestItems(settledItems(await Promise.allSettled(requests)))
  } finally {
    homeLists.recentLoading = false
  }
}

async function loadDashboard() {
  if (hasPerm(SPACE_LIST)) {
    try {
      const result = await pageSpaces({ pageNum: 1, pageSize: 1 })
      dashboard.spaceTotal = result.total
    } catch {
      // 统计失败时保持占位，不影响首页其他功能
    }
  }
  dashboard.loading = false
}
onMounted(() => {
  loadDashboard()
  loadPendingItems()
  loadRecentItems()
})

const kpis = computed(() => hasPerm(SPACE_LIST)
  ? [{ label: '场地', icon: Location, value: dashboard.spaceTotal }]
  : [])

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

const ROLE_TEXT = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生', OPS_ADMIN: '运维管理员' }
const roleText = computed(() => store.roles.map((r) => ROLE_TEXT[r] || r).join(' · ') || '实验室用户')

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
  ...(hasPerm('item:material:list') && openPaths.value.has('/admin/item/workspace') ? [{
    name: '物料工作台',
    desc: '物料档案、库存与申领处理',
    icon: Box,
    tone: 'brand',
    path: '/admin/item/workspace'
  }] : []),
  ...(hasPerm('item:claim:list') && openPaths.value.has('/item/claims') ? [{
    name: '我的物料申领',
    desc: '申领、归还与逾期处理',
    icon: Box,
    tone: 'ok',
    path: '/item/claims'
  }] : []),
  ...(hasPerm('item:pickup:list') && openPaths.value.has('/item/pickups') ? [{
    name: '我的代领任务',
    desc: '查看老师推送的物料代领安排',
    icon: Box,
    tone: 'ok',
    path: '/item/pickups'
  }] : []),
  ...(hasPerm('item:plan:list') && openPaths.value.has('/item/plans') ? [{
    name: '我的预设方案',
    desc: '维护常用物料申领方案',
    icon: Document,
    tone: 'brand',
    path: '/item/plans'
  }] : []),
  ...(hasPerm('item:notification:list') ? [{
    name: '通知消息',
    desc: notification.unreadCount ? `${notification.unreadCount} 条未读通知` : '审批与催办提醒',
    icon: Bell,
    tone: 'brand',
    action: 'notifications',
    path: ''
  }] : [])
].filter((entry) => entry.path || entry.action))

function go(entry) {
  if (entry.action === 'notifications') {
    notification.openDrawer()
    return
  }
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

.home_list {
  display: flex;
  flex-direction: column;
}

.home_list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border: 0;
  border-bottom: .5px solid var(--hair);
  background: transparent;
  color: var(--g2);
  text-align: left;
  cursor: pointer;
}

.home_list__item:last-child {
  border-bottom: 0;
}

.home_list__item:hover {
  color: var(--brand);
}

.home_list__content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 3px;
}

.home_list__content strong,
.home_list__content span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home_list__content strong {
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.home_list__content span,
.home_list__item time {
  font-size: 12px;
  color: var(--g3);
}

.home_list__item time {
  flex-shrink: 0;
}

@media (max-width: 900px) {
  .home_view__grid { grid-template-columns: repeat(2, 1fr); }
  .home_view__columns { grid-template-columns: 1fr; }
}
</style>
