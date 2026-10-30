<template>
  <el-drawer
    :model-value="modelValue"
    title="场地详情"
    size="520px"
    @update:model-value="(v) => !v && emit('close')"
  >
    <div
      v-if="space"
      class="space_detail"
    >
      <div class="card space_detail__head">
        <div class="space_detail__title_row">
          <h3 class="t-title space_detail__name">
            {{ space.labName }}
          </h3>
          <span
            class="pill"
            :class="SPACE_STATUS_MAP[space.status]?.pill"
          >
            {{ SPACE_STATUS_MAP[space.status]?.label || space.status }}
          </span>
        </div>
        <p class="t-sub">
          {{ space.locationDetail || '位置未填写' }} · {{ SPACE_TYPE_MAP[space.labType]?.label || space.labType }}
        </p>
      </div>

      <!-- 当前使用状态：场地状态 + 进行中预约 + 开放时段派生 -->
      <div class="space_detail__status_card">
        <div>
          <p class="space_detail__status_label">
            当前使用状态
          </p>
          <p class="space_detail__status_value">
            {{ usage.label }}
          </p>
        </div>
        <span
          class="pill"
          :class="usage.pill"
        >
          {{ usage.label }}
        </span>
      </div>
      <p class="space_detail__status_hint">
        {{ usage.hint }}
      </p>

      <div class="card">
        <h4 class="space_detail__section">
          基础信息
        </h4>
        <el-descriptions
          :column="2"
          border
        >
          <el-descriptions-item
            label="场地编号"
            :span="2"
          >
            <span class="space_detail__mono">{{ space.id }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="场地类型">
            {{ SPACE_TYPE_MAP[space.labType]?.label || space.labType }}
          </el-descriptions-item>
          <el-descriptions-item label="容纳人数">
            {{ space.capacity }} 人
          </el-descriptions-item>
          <el-descriptions-item label="所属部门">
            设备处
          </el-descriptions-item>
          <el-descriptions-item label="场地状态">
            {{ SPACE_STATUS_MAP[space.status]?.label || space.status }}
          </el-descriptions-item>
          <el-descriptions-item
            label="位置"
            :span="2"
          >
            {{ space.locationDetail || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="开放时段">
            {{ space.openStart }} - {{ space.openEnd }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ SPACE_STATUS_MAP[space.status]?.label || space.status }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatShanghai(space.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatShanghai(space.updateTime) }}
          </el-descriptions-item>
        </el-descriptions>
        <p class="t-hint space_detail__tz">
          开放时段为上海本地时间；时间点为 UTC 存储，此处按上海时区展示
        </p>
      </div>

      <div class="card">
        <h4 class="space_detail__section">
          设备配置清单
        </h4>
        <p class="space_detail__facilities">
          {{ space.facilities || '暂未登记配套设备' }}
        </p>
      </div>

      <div class="card">
        <div class="space_detail__section_head">
          <h4 class="space_detail__section">
            近期排课与占用
          </h4>
          <span class="t-hint">未来 7 天</span>
        </div>
        <el-skeleton
          v-if="bookingsLoading"
          :rows="3"
          animated
        />
        <p
          v-else-if="!scheduleGroups.length"
          class="space_detail__empty"
        >
          未来 7 天暂无预约或维修安排，场地各时段可用
        </p>
        <div
          v-else
          class="space_detail__schedule"
        >
          <div
            v-for="group in scheduleGroups"
            :key="group.date"
            class="space_detail__schedule_day"
          >
            <p class="space_detail__schedule_date">
              {{ group.label }}
            </p>
            <div
              v-for="item in group.items"
              :key="item.id"
              class="space_detail__schedule_item"
            >
              <span class="space_detail__schedule_time">{{ item.timeText }}</span>
              <div class="space_detail__schedule_body">
                <p class="space_detail__schedule_title">
                  {{ item.title }}
                </p>
                <p class="space_detail__schedule_sub">
                  {{ item.sub || '—' }}
                </p>
              </div>
              <span
                class="pill"
                :class="item.pill"
              >
                {{ item.statusText }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <h4 class="space_detail__section">
          最近使用记录
        </h4>
        <el-skeleton
          v-if="bookingsLoading"
          :rows="2"
          animated
        />
        <p
          v-else-if="!recentUsages.length"
          class="space_detail__empty"
        >
          暂无已结束的使用记录
        </p>
        <ul
          v-else
          class="space_detail__usage_list"
        >
          <li
            v-for="item in recentUsages"
            :key="item.id"
          >
            <div>
              <p class="space_detail__usage_title">
                {{ item.title }}
              </p>
              <p class="space_detail__usage_sub">
                {{ item.sub || '—' }}
              </p>
            </div>
            <span class="space_detail__usage_time">{{ item.timeText }}</span>
          </li>
        </ul>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { SPACE_STATUS_MAP, SPACE_TYPE_MAP } from './space.constants'
import { pageBoardBookings } from '@/api/booking'
import { listMaintenanceWindows } from '@/api/space'
import {
  addDaysShanghai,
  formatInstant,
  formatRange,
  formatShanghaiDate,
  nowShanghaiClock,
  todayShanghai
} from '@/utils/datetime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  space: { type: Object, default: null }
})

const emit = defineEmits(['close'])

// ---------------------------------------------------------------------------
// 排课占用与使用记录：由本组件自行加载（预约只读分页，需 booking:lab:view；
// 无权限时降级为仅展示档案，不影响基础信息）。
// ---------------------------------------------------------------------------
const bookings = ref([])
const maintenance = ref([])
const bookingsLoading = ref(false)
const nowTick = ref(Date.now())
let tickTimer = null

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const BOOKING_STATUS_META = {
  PENDING: { label: '待审批', pill: 'pill--warn' },
  APPROVED: { label: '已批准', pill: 'pill--ok' },
  REJECTED: { label: '已驳回', pill: 'pill--danger' },
  CANCELLED: { label: '已取消', pill: 'pill--muted' }
}

/** 当前使用状态：场地停用/维护优先，其次判断进行中的已批准预约，最后按开放时段派生 */
const usage = computed(() => {
  const now = nowTick.value
  const space = props.space
  if (!space) {
    return { label: '—', pill: 'pill--muted', hint: '' }
  }
  if (space.status === 'CLOSED') {
    return { label: '已停用', pill: 'pill--muted', hint: '场地已停用，不可新建预约' }
  }
  if (space.status === 'MAINTAINING') {
    return { label: '维护中', pill: 'pill--warn', hint: '场地维护中，暂不可预约' }
  }
  const active = bookings.value.find(
    (b) => b.status === 'APPROVED' && Date.parse(b.startAt) <= now && now < Date.parse(b.endAt)
  )
  if (active) {
    const range = `${formatInstant(active.startAt, { withDate: false })}-${formatInstant(active.endAt, { withDate: false })}`
    return {
      label: '使用中',
      pill: 'pill--brand',
      hint: `当前课程：${active.courseName || active.purpose || '场地预约'}（${range}）`
    }
  }
  const clock = nowShanghaiClock()
  if (clock < space.openStart || clock >= space.openEnd) {
    return {
      label: '非开放时段',
      pill: 'pill--muted',
      hint: `开放时间 ${space.openStart}-${space.openEnd}（上海本地）`
    }
  }
  return { label: '空闲可用', pill: 'pill--ok', hint: '当前无进行中的预约，可在开放时段内预约' }
})

/** 未来 7 天待审批/已批准预约（含进行中），按日期与开始时间升序 */
const upcoming = computed(() => {
  const now = nowTick.value
  const today = todayShanghai()
  const endDate = addDaysShanghai(today, 7)
  return bookings.value
    .filter((b) => (b.status === 'APPROVED' || b.status === 'PENDING') && b.bookingDate)
    .filter((b) => b.bookingDate >= today && b.bookingDate <= endDate)
    .filter((b) => Date.parse(b.endAt) > now)
    .sort((a, b) => {
      if (a.bookingDate !== b.bookingDate) return a.bookingDate.localeCompare(b.bookingDate)
      return Date.parse(a.startAt) - Date.parse(b.startAt)
    })
})

/** 按日期分组的排课/占用/维修卡片（组按日期升序，组内按开始时间升序） */
const scheduleGroups = computed(() => {
  const groups = new Map()
  for (const booking of upcoming.value) {
    const key = booking.bookingDate
    if (!groups.has(key)) {
      groups.set(key, { date: key, label: dateLabel(key), items: [] })
    }
    groups.get(key).items.push({ ...toScheduleItem(booking), startMs: Date.parse(booking.startAt) })
  }
  for (const item of maintenanceItems.value) {
    if (!groups.has(item.date)) {
      groups.set(item.date, { date: item.date, label: dateLabel(item.date), items: [] })
    }
    groups.get(item.date).items.push(item)
  }
  const sorted = [...groups.values()].sort((a, b) => a.date.localeCompare(b.date))
  for (const group of sorted) {
    group.items.sort((a, b) => a.startMs - b.startMs)
  }
  return sorted
})

/** 未来 7 天维修时段条目（进行中+未结束），按上海开始日期混入排课分组 */
const maintenanceItems = computed(() => {
  const now = nowTick.value
  const endDate = addDaysShanghai(todayShanghai(), 7)
  return maintenance.value
    .filter((w) => Date.parse(w.endAt) > now)
    .filter((w) => formatShanghaiDate(w.startAt) <= endDate)
    .map((w) => {
      const date = formatShanghaiDate(w.startAt)
      const sameDay = date === formatShanghaiDate(w.endAt)
      const s = formatInstant(w.startAt, { withDate: false })
      const e = formatInstant(w.endAt, { withDate: false })
      return {
        id: `mw-${w.id}`,
        date,
        startMs: Date.parse(w.startAt),
        timeText: sameDay ? `${s}-${e}` : formatRange(w.startAt, w.endAt),
        title: '维修时段',
        sub: w.reason || '维修期间暂停预约',
        statusText: Date.parse(w.startAt) <= now ? '维修中' : '维修安排',
        pill: 'pill--danger'
      }
    })
})

/** 最近使用记录：已批准且已结束的预约，按开始时间倒序取 5 条 */
const recentUsages = computed(() => {
  const now = nowTick.value
  return bookings.value
    .filter((b) => b.status === 'APPROVED' && b.endAt && Date.parse(b.endAt) <= now)
    .sort((a, b) => Date.parse(b.startAt) - Date.parse(a.startAt))
    .slice(0, 5)
    .map((b) => ({
      id: b.id,
      title: b.courseName || b.purpose || '场地使用',
      sub: [
        b.className,
        b.teacherName || b.applicantName,
        b.checkinAt ? `签到 ${formatInstant(b.checkinAt, { withDate: false })}` : '',
        b.checkoutAt ? `结束 ${formatInstant(b.checkoutAt, { withDate: false })}` : ''
      ]
        .filter(Boolean)
        .join(' · '),
      timeText: `${b.bookingDate} ${formatInstant(b.startAt, { withDate: false })}`
    }))
})

/** 日期标签：今天/明天带前缀，其余「M月D日 周X」 */
function dateLabel(dateStr) {
  const [y, m, d] = String(dateStr).split('-').map(Number)
  if ([y, m, d].some((n) => Number.isNaN(n))) return dateStr
  const weekday = WEEKDAY_LABELS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]
  const today = todayShanghai()
  if (dateStr === today) return `今天 ${m}月${d}日 ${weekday}`
  if (dateStr === addDaysShanghai(today, 1)) return `明天 ${m}月${d}日 ${weekday}`
  return `${m}月${d}日 ${weekday}`
}

/** 节次区间 + 上海本地时分 */
function periodText(booking) {
  const start = booking.startPeriodName
  const end = booking.endPeriodName
  if (start && end) return start === end ? start : `${start}-${end}`
  return start || ''
}

function bookingStatusMeta(booking) {
  if (booking.status === 'APPROVED' && booking.runtimePhase === 'IN_USE') {
    return { label: '使用中', pill: 'pill--brand' }
  }
  if (booking.status === 'APPROVED' && booking.runtimePhase === 'FINISHED') {
    return { label: '已结束', pill: 'pill--muted' }
  }
  return BOOKING_STATUS_META[booking.status] || { label: booking.status, pill: 'pill--muted' }
}

function toScheduleItem(booking) {
  const meta = bookingStatusMeta(booking)
  const time = `${formatInstant(booking.startAt, { withDate: false })}-${formatInstant(booking.endAt, { withDate: false })}`
  return {
    id: booking.id,
    timeText: [periodText(booking), time].filter(Boolean).join(' '),
    title: booking.courseName || booking.purpose || '场地预约',
    sub: [booking.className, booking.teacherName || booking.applicantName].filter(Boolean).join(' · '),
    statusText: meta.label,
    pill: meta.pill
  }
}

function startTick() {
  stopTick()
  nowTick.value = Date.now()
  tickTimer = window.setInterval(() => {
    nowTick.value = Date.now()
  }, 30_000)
}

function stopTick() {
  if (tickTimer) {
    window.clearInterval(tickTimer)
    tickTimer = null
  }
}

// 请求序号：快速切换场地或关闭抽屉时丢弃过期响应，避免旧数据覆盖新数据
let loadSeq = 0

/** 抽屉打开且已确定场地时加载预约与维修数据；关闭时清空并停止刷新 */
watch(
  () => [props.modelValue, props.space?.id],
  async ([visible, spaceId]) => {
    const seq = ++loadSeq
    if (!visible || !spaceId) {
      bookings.value = []
      maintenance.value = []
      bookingsLoading.value = false
      stopTick()
      return
    }
    bookingsLoading.value = true
    startTick()
    try {
      const [page, windows] = await Promise.all([
        pageBoardBookings({ spaceId, pageNum: 1, pageSize: 100 }).catch(() => null),
        listMaintenanceWindows(spaceId).catch(() => [])
      ])
      if (seq !== loadSeq) return
      bookings.value = page?.records || []
      maintenance.value = windows || []
    } finally {
      if (seq === loadSeq) bookingsLoading.value = false
    }
  },
  { immediate: true }
)

onUnmounted(stopTick)

/** UTC ISO 时间点按上海时区展示（不依赖浏览器本地时区） */
function formatShanghai(value) {
  return value ? formatInstant(value) : '—'
}
</script>

<style scoped>
.space_detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0 4px 24px;
}

.space_detail__head {
  padding: 18px;
}

.space_detail__title_row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.space_detail__name {
  font-size: 18px;
  margin: 0;
}

.space_detail__section {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.space_detail__section_head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.space_detail__section_head .space_detail__section {
  margin-bottom: 0;
}

.space_detail__section_head + .space_detail__empty,
.space_detail__section_head + .space_detail__schedule {
  margin-top: 12px;
}

.space_detail__status_card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 18px;
  background: var(--surface-sunken);
  border: .5px solid var(--hair);
  border-radius: var(--r-md);
}

.space_detail__status_label {
  margin: 0;
  font-size: 12px;
  color: var(--g3);
}

.space_detail__status_value {
  margin: 4px 0 0;
  font-size: 18px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.space_detail__status_hint {
  margin: -8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--g3);
}

.space_detail__mono {
  font-family: var(--font-num);
  font-size: 12px;
  color: var(--g2);
  word-break: break-all;
}

.space_detail__tz {
  margin: 10px 0 0;
}

.space_detail__facilities {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.space_detail__empty {
  margin: 0;
  padding: 12px;
  border: .5px dashed var(--hair-strong);
  border-radius: var(--r-sm);
  font-size: 12px;
  color: var(--g3);
  text-align: center;
}

.space_detail__schedule {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.space_detail__schedule_day {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.space_detail__schedule_date {
  margin: 0;
  font-size: 12px;
  font-weight: var(--fw-semibold);
  color: var(--g2);
}

.space_detail__schedule_item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 10px;
  background: var(--surface-sunken);
  border: .5px solid var(--hair);
  border-radius: var(--r-sm);
}

.space_detail__schedule_time {
  flex: 0 0 88px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--g3);
  font-variant-numeric: tabular-nums;
}

.space_detail__schedule_body {
  flex: 1;
  min-width: 0;
}

.space_detail__schedule_title {
  margin: 0;
  font-size: 13px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space_detail__schedule_sub {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--g3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space_detail__usage_list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.space_detail__usage_list li {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 10px;
  background: var(--surface-sunken);
  border: .5px solid var(--hair);
  border-radius: var(--r-sm);
}

.space_detail__usage_title {
  margin: 0;
  font-size: 13px;
  color: var(--g1);
}

.space_detail__usage_sub {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--g3);
}

.space_detail__usage_time {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--g3);
  font-variant-numeric: tabular-nums;
}
</style>