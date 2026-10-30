<template>
  <div class="tl">
    <!-- 页头 -->
    <div class="tl__head">
      <div>
        <h2 class="t-title tl__title">
          场地预约时间轴
        </h2>
        <p class="t-sub">
          {{ isReadonly
            ? '只读查看：选日期看全部场地状态，点场地卡片看各节次占用 · 时段按上海本地时间展示'
            : '选日期看全部场地三色状态 · 点场地卡片进入节次视图，点按空闲节次（可跳选多节）后发起预约或批量周期预约' }}
        </p>
      </div>
      <div
        v-if="!isReadonly"
        class="tl__actions"
      >
        <el-button
          v-permission="'booking:lab:create'"
          @click="openSeries"
        >
          批量周期预约
        </el-button>
        <el-button
          v-permission="'booking:lab:create'"
          type="primary"
          :disabled="!canBookSelected"
          @click="openBook"
        >
          发起预约
        </el-button>
      </div>
    </div>

    <!-- 筛选条：Typeahead 场地 + 日期 + 节次筛选 -->
    <div class="card tl__filters">
      <el-form
        :inline="true"
        :model="filter"
        class="tl__form"
      >
        <el-form-item label="场地">
          <el-autocomplete
            v-model="filter.keyword"
            :fetch-suggestions="querySpaces"
            placeholder="输入场地名称模糊查询"
            clearable
            class="tl__space"
            value-key="labName"
            @select="onSelectSpace"
            @clear="onClearSpace"
          >
            <template #default="{ item }">
              <div class="tl__suggest">
                <b>{{ item.labName }}</b>
                <span class="t-hint">{{ item.capacity }} 人 · {{ item.statusName }}</span>
              </div>
            </template>
          </el-autocomplete>
        </el-form-item>
        <el-form-item v-if="selectedSpace">
          <span class="pill pill--ok">{{ selectedSpace.labName }}</span>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="filter.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :disabled-date="disablePast"
            class="tl__date"
            @change="onDateChanged"
          />
        </el-form-item>
        <el-form-item label="节次筛选">
          <el-select
            v-model="filter.periodNo"
            placeholder="全部"
            clearable
            class="tl__period_filter"
            @change="onPeriodFiltered"
          >
            <el-option
              v-for="p in periods"
              :key="p.periodNo"
              :label="periodLabel(p)"
              :value="p.periodNo"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <!-- 场地三色总览（只选日期未选场地） -->
    <div
      v-if="!filter.spaceId"
      class="card tl__periods"
    >
      <div class="tl__periods_head">
        <h3 class="tl__block_title">
          {{ filter.date || today }} 全部场地状态
        </h3>
        <div class="tl__legend">
          <span class="tl__legend_item"><i class="tl__dot tl__dot--free" />全天空闲</span>
          <span class="tl__legend_item"><i class="tl__dot tl__dot--pending" />部分空闲</span>
          <span class="tl__legend_item"><i class="tl__dot tl__dot--occupied" />无空闲</span>
          <span class="tl__legend_item"><i class="tl__dot tl__dot--past" />有维修安排</span>
        </div>
      </div>
      <div
        v-loading="overviewLoading"
        class="tl__period_grid"
      >
        <button
          v-for="card in spaceCards"
          :key="card.spaceId"
          type="button"
          class="period_card"
          :class="SPACE_CARD_MAP[card.state].cls"
          :title="isReadonly ? `${card.labName} · 查看节次占用` : `${card.labName} · 点击进入节次视图预约`"
          @click="onSpaceCardClick(card)"
        >
          <b class="period_card__no">{{ card.labName }}</b>
          <span class="period_card__clock">容量 {{ card.capacity }} 人 · {{ card.openStart }}-{{ card.openEnd }}</span>
          <span class="period_card__state">{{ SPACE_CARD_MAP[card.state].tip }}</span>
        </button>
        <p
          v-if="!spaceCards.length && !overviewLoading"
          class="t-hint tl__periods_empty"
        >
          该日期暂无可用场地
        </p>
      </div>
    </div>

    <!-- 节次三色小卡片（选了场地 + 日期） -->
    <div
      v-else
      class="card tl__periods"
    >
      <div class="tl__periods_head">
        <h3 class="tl__block_title">
          {{ selectedSpace ? selectedSpace.labName : '' }} · {{ filter.date || today }} 节次安排
        </h3>
        <div class="tl__legend">
          <span class="tl__legend_item"><i class="tl__dot tl__dot--free" />空闲可点选</span>
          <span class="tl__legend_item"><i class="tl__dot tl__dot--occupied" />已占用</span>
          <span class="tl__legend_item"><i class="tl__dot tl__dot--past" />维修中</span>
          <span class="tl__legend_item"><i class="tl__dot tl__dot--pending" />下节课即将占用</span>
          <span class="tl__legend_item"><i class="tl__dot tl__dot--past" />已过去</span>
        </div>
      </div>
      <div
        v-loading="periodsLoading"
        class="tl__period_grid"
      >
        <button
          v-for="card in cards"
          :key="card.periodNo"
          type="button"
          class="period_card"
          :class="[PERIOD_CARD_MAP[card.state].cls, { 'period_card--selected': isSelected(card.periodNo), 'period_card--dim': filter.periodNo && card.periodNo !== filter.periodNo }]"
          :disabled="card.state !== 'free' || isReadonly"
          :title="PERIOD_CARD_MAP[card.state].tip"
          @click="onPeriodClick(card)"
        >
          <b class="period_card__no">{{ card.periodName }}</b>
          <span class="period_card__clock">{{ card.startClock }}-{{ card.endClock }}</span>
          <span class="period_card__state">{{ PERIOD_CARD_MAP[card.state].tip }}</span>
        </button>
        <p
          v-if="!cards.length && !periodsLoading"
          class="t-hint tl__periods_empty"
        >
          该场地当日无节次数据
        </p>
      </div>
      <div
        v-if="selectionTip"
        class="tl__selection"
      >
        <span class="pill pill--brand">已选 {{ selectionTip }}</span>
        <el-button
          link
          type="primary"
          @click="selectedPeriodNos = []"
        >
          取消选择
        </el-button>
      </div>
    </div>

    <!-- 24h 近期预约 -->
    <div class="card tl__recent">
      <div class="tl__recent_head">
        <div>
          <h3 class="tl__block_title">
            近期预约（24 小时）
          </h3>
          <p class="t-hint">
            {{ selectedSpace ? `${selectedSpace.labName} 未来 24 小时内的待审批/已批准预约` : '先在上方选择场地' }}
          </p>
        </div>
        <span
          v-if="selectedSpace"
          class="t-hint"
        >
          {{ recent.length }} 条
        </span>
      </div>
      <el-table
        v-if="selectedSpace"
        v-loading="recentLoading"
        :data="recent"
        class="table-scroll-x"
        row-key="id"
      >
        <el-table-column
          label="课程 / 班级"
          min-width="150"
        >
          <template #default="{ row }">
            <span class="tl__recent_main">{{ row.courseName || row.purpose || '—' }}</span>
            <span v-if="row.className" class="t-hint"> · {{ row.className }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="使用时段（上海）"
          min-width="170"
        >
          <template #default="{ row }">
            {{ formatRange(row.startAt, row.endAt) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="applicantName"
          label="申请人"
          width="100"
        />
        <el-table-column
          label="状态"
          width="96"
        >
          <template #default="{ row }">
            <el-tooltip
              v-if="row.rejectReason"
              :content="`驳回原因：${row.rejectReason}`"
              placement="top"
            >
              <span class="pill pill--danger">已驳回</span>
            </el-tooltip>
            <span
              v-else
              class="pill"
              :class="BOOKING_STATUS_MAP[row.status]?.pill"
            >
              {{ BOOKING_STATUS_MAP[row.status]?.label || row.status }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <p
        v-else
        class="t-hint tl__recent_empty"
      >
        选择场地后展示该场地 24 小时内的近期预约，不做历史前后记录
      </p>
    </div>

    <!-- 我的预约 / 预约情况（只读） -->
    <div class="card tl__list">
      <div class="tl__list_head">
        <h3 class="tl__block_title">
          {{ isReadonly ? '预约情况' : '我的预约' }}
        </h3>
      </div>
      <el-table
        v-loading="listLoading"
        :data="records"
        class="table-scroll-x"
        row-key="id"
      >
        <el-table-column
          label="场地"
          min-width="140"
        >
          <template #default="{ row }">
            <span class="tl__recent_main">{{ row.labName || row.spaceId }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="课程 / 班级"
          min-width="150"
        >
          <template #default="{ row }">
            {{ [row.courseName, row.className].filter(Boolean).join(' · ') || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="老师"
          min-width="90"
        >
          <template #default="{ row }">
            {{ row.teacherName || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="使用时段（上海）"
          min-width="180"
        >
          <template #default="{ row }">
            {{ formatRange(row.startAt, row.endAt) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="purpose"
          label="事由"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          label="状态"
          width="110"
        >
          <template #default="{ row }">
            <el-tooltip
              v-if="row.status === 'REJECTED' && row.rejectReason"
              :content="`驳回原因：${row.rejectReason}`"
              placement="top"
            >
              <span class="pill pill--danger">已驳回</span>
            </el-tooltip>
            <span
              v-else-if="row.status === 'APPROVED' && row.runtimePhase"
              class="pill"
              :class="RUNTIME_PHASE_MAP[row.runtimePhase]?.pill"
            >
              {{ RUNTIME_PHASE_MAP[row.runtimePhase]?.label || row.runtimePhase }}
            </span>
            <span
              v-else
              class="pill"
              :class="BOOKING_STATUS_MAP[row.status]?.pill"
            >
              {{ BOOKING_STATUS_MAP[row.status]?.label || row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="150"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openDetail(row)"
            >
              详情
            </el-button>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'APPROVED'"
              v-permission="'booking:lab:cancel'"
              link
              type="danger"
              @click="onCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="t-hint">{{ isReadonly ? '暂无预约记录' : '暂无预约记录，可点选空闲节次发起预约' }}</span>
        </template>
      </el-table>

      <div class="tl__pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <SpaceBookDialog
      v-model="bookVisible"
      :preset-space-id="filter.spaceId"
      :preset-date="filter.date"
      :preset-segments="selectedSegments"
      @close="bookVisible = false"
      @submit="onBookingSubmitted"
    />
    <SeriesBookDialog
      v-model="seriesVisible"
      :preset-space-id="filter.spaceId"
      :preset-date="filter.date"
      :preset-segments="selectedSegments"
      @close="seriesVisible = false"
      @submit="onSeriesSubmitted"
    />
    <MyBookingDetailDrawer
      v-model="detailVisible"
      :booking-id="detailId"
      @close="detailVisible = false"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useUserStore } from '@/store/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import SpaceBookDialog from './SpaceBookDialog.vue'
import SeriesBookDialog from './SeriesBookDialog.vue'
import MyBookingDetailDrawer from './MyBookingDetailDrawer.vue'
import { BOOKING_STATUS_MAP, RUNTIME_PHASE_MAP, PERIOD_CARD_MAP, SPACE_CARD_MAP } from './booking.constants'
import { listSpaceOptions } from '@/api/space'
import {
  getAvailability,
  getAvailabilityAll,
  listPeriods,
  listRecentBookings,
  pageMyBookings,
  pageBoardBookings,
  createBooking,
  cancelBooking
} from '@/api/booking'
import {
  formatRange,
  shanghaiLocalToIso,
  todayShanghai,
  nowShanghaiClock,
  shanghaiClockOf
} from '@/utils/datetime'

const userStore = useUserStore()
const isReadonly = computed(() => userStore.user?.userType === 'STUDENT')

const today = todayShanghai()
const toMin = (t) => (Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5)))

const periods = ref([])
const spaces = ref([])
const availability = ref(null)
const overview = ref([])
const recent = ref([])
const records = ref([])
const total = ref(0)

const periodsLoading = ref(false)
const overviewLoading = ref(false)
const recentLoading = ref(false)
const listLoading = ref(false)

const bookVisible = ref(false)
const seriesVisible = ref(false)
const detailVisible = ref(false)
const detailId = ref('')

// 顶部筛选：场地 Typeahead + 日期 + 节次筛选
const filter = reactive({
  keyword: '',
  spaceId: '',
  date: today,
  periodNo: null
})

// 节次点按多选（节次号数组，可跳选不连续；提交时按连续段拆分多预约单）
const selectedPeriodNos = ref([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: null
})

const selectedSpace = computed(() => spaces.value.find((s) => s.id === filter.spaceId) || null)

function periodLabel(p) {
  return `${p.periodName} ${p.startClock}-${p.endClock}`
}

/** Typeahead 联想：场地名称模糊查询，仅可用场地进入选择列表 */
async function querySpaces(keyword, cb) {
  const kw = typeof keyword === 'string' ? keyword.trim() : ''
  try {
    const list = await listSpaceOptions({ availableOnly: true, keyword: kw || undefined })
    spaces.value = list || []
    cb(structuredClone(list || []))
  } catch {
    cb([])
  }
}

function onSelectSpace(item) {
  filter.spaceId = item.id
  filter.keyword = item.labName
  selectedPeriodNos.value = []
  refreshTimeline()
}

/** 场地总览卡片点击：选中该场地进入节次视图（学生只读也可查看占用） */
function onSpaceCardClick(card) {
  const hit = spaces.value.find((s) => s.id === card.spaceId)
  if (hit) {
    onSelectSpace(hit)
  } else {
    // typeahead 缓存未命中时直接按总览数据置入选中态
    filter.spaceId = card.spaceId
    filter.keyword = card.labName
    spaces.value = [...spaces.value.filter((s) => s.id !== card.spaceId),
      { id: card.spaceId, labName: card.labName, capacity: card.capacity }]
    selectedPeriodNos.value = []
    refreshTimeline()
  }
}

function onClearSpace() {
  filter.spaceId = ''
  filter.keyword = ''
  availability.value = null
  recent.value = []
  selectedPeriodNos.value = []
  loadOverview()
}

function onDateChanged() {
  selectedPeriodNos.value = []
  if (filter.spaceId) {
    refreshTimeline()
  } else {
    loadOverview()
  }
}

function onPeriodFiltered() {
  // 节次筛选：仅高亮对应卡片；点选逻辑不受影响
}

function disablePast(date) {
  const [y, m, d] = today.split('-').map(Number)
  return date.getTime() < Date.UTC(y, m - 1, d)
}

/** 某节次是否被占用段命中（半开区间重叠；segments 为 UTC 占用段数组） */
function isOccupiedBy(segments, period) {
  return (segments || []).some((seg) => {
    const s = shanghaiClockOf(seg.startAt)
    const e = shanghaiClockOf(seg.endAt)
    if (!s || !e) return false
    const segStart = toMin(s)
    const segEnd = toMin(e)
    return segEnd > toMin(period.startClock) && segStart < toMin(period.endClock)
  })
}

function isOccupied(period) {
  return isOccupiedBy(availability.value?.occupied, period)
}

/** 某节次是否被生效中维修时段覆盖（维修窗口内禁止预约，卡片标灰） */
function isMaintenance(period) {
  return isOccupiedBy(availability.value?.maintenance, period)
}

/** 节次卡片状态派生（前端按当前时间计算，不落库；维修优先于占用展示） */
const cards = computed(() => {
  const nowClock = nowShanghaiClock()
  const nowMin = toMin(nowClock)
  return periods.value.map((p, idx) => {
    const endMin = toMin(p.endClock)
    let state
    if (filter.date < today) {
      state = 'past'
    } else if (filter.date === today && endMin <= nowMin) {
      state = 'past'
    } else if (isMaintenance(p)) {
      state = 'maintenance'
    } else if (isOccupied(p)) {
      state = 'occupied'
    } else {
      const next = periods.value[idx + 1]
      state = next && (isOccupied(next) || isMaintenance(next)) ? 'pending' : 'free'
    }
    return { ...p, state }
  })
})

/** 场地三色总览派生：当日有维修时段=灰（优先）；未过去节次全空闲=绿；全占用=红；其余=橙 */
const spaceCards = computed(() => {
  const nowMin = toMin(nowShanghaiClock())
  const remaining = periods.value.filter((p) => {
    if (filter.date > today) return true
    if (filter.date < today) return false
    return toMin(p.endClock) > nowMin
  })
  return overview.value.map((s) => {
    let state
    if (s.maintenance && s.maintenance.length) {
      state = 'maintenance'
    } else if (!remaining.length) {
      state = 'none'
    } else {
      const freeCount = remaining.filter((p) => !isOccupiedBy(s.occupied, p)).length
      state = freeCount === remaining.length ? 'all' : (freeCount === 0 ? 'none' : 'partial')
    }
    return { ...s, state }
  })
})

function isSelected(periodNo) {
  return selectedPeriodNos.value.includes(periodNo)
}

/** 节次点按 toggle：空闲节次可任意跳选/取消，组合在提交时按连续段拆分 */
function onPeriodClick(card) {
  if (isReadonly.value) return
  if (card.state !== 'free') {
    ElMessage.warning(PERIOD_CARD_MAP[card.state].tip)
    return
  }
  const idx = selectedPeriodNos.value.indexOf(card.periodNo)
  if (idx >= 0) {
    selectedPeriodNos.value = selectedPeriodNos.value.filter((n) => n !== card.periodNo)
  } else {
    selectedPeriodNos.value = [...selectedPeriodNos.value, card.periodNo].sort((a, b) => a - b)
  }
}

/** 已选节次的连续段分组：[3,4,5,8] → [[3,5],[8,8]]（提交拆单依据） */
const selectedSegments = computed(() => {
  const sorted = [...selectedPeriodNos.value].sort((a, b) => a - b)
  const groups = []
  for (const n of sorted) {
    const last = groups[groups.length - 1]
    if (last && n === last[1] + 1) {
      last[1] = n
    } else {
      groups.push([n, n])
    }
  }
  return groups
})

const selectionTip = computed(() => {
  if (!selectedPeriodNos.value.length) return ''
  const names = selectedPeriodNos.value
    .map((no) => periods.value.find((p) => p.periodNo === no)?.periodName || `第${no}节`)
  const segCount = selectedSegments.value.length
  const suffix = segCount > 1 ? `，将拆为 ${segCount} 个预约单` : ''
  return `${names.join('、')}（共 ${names.length} 节${suffix}）`
})

const canBookSelected = computed(() => !isReadonly.value && !!filter.spaceId && !!filter.date && !!selectedPeriodNos.value.length)

function openBook() {
  if (!canBookSelected.value) {
    ElMessage.warning('请先选择场地，并在节次卡片中点按空闲节次')
    return
  }
  bookVisible.value = true
}

function openSeries() {
  if (!filter.spaceId) {
    ElMessage.warning('请先点上方场地卡片或输入场地名称选择场地')
    return
  }
  if (!selectedPeriodNos.value.length) {
    ElMessage.warning('请先在节次卡片中点按空闲节次')
    return
  }
  seriesVisible.value = true
}

async function refreshTimeline() {
  if (!filter.spaceId || !filter.date) {
    availability.value = null
    recent.value = []
    return
  }
  periodsLoading.value = true
  recentLoading.value = true
  try {
    // 占用段供节次三色与表单辅助展示
    try {
      availability.value = await getAvailability(filter.spaceId, filter.date)
    } catch {
      availability.value = null
    }
    recent.value = await listRecentBookings(filter.spaceId)
  } finally {
    periodsLoading.value = false
    recentLoading.value = false
  }
  // 占用/维修状态刷新后，清掉已选中但当前不可约的节次（避免选中项卡死）
  if (selectedPeriodNos.value.length) {
    const freeNos = new Set(cards.value.filter((c) => c.state === 'free').map((c) => c.periodNo))
    const kept = selectedPeriodNos.value.filter((no) => freeNos.has(no))
    if (kept.length !== selectedPeriodNos.value.length) {
      selectedPeriodNos.value = kept
      ElMessage.warning('部分已选节次已变为不可约（被占用或维修），已自动移除')
    }
  }
  loadList()
}

/** 全部场地当日占用总览（只选日期的三色场地卡片） */
async function loadOverview() {
  if (!filter.date) {
    overview.value = []
    return
  }
  overviewLoading.value = true
  try {
    overview.value = (await getAvailabilityAll(filter.date)) || []
  } catch {
    overview.value = []
  } finally {
    overviewLoading.value = false
  }
  loadList()
}

async function loadList() {
  listLoading.value = true
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      spaceId: filter.spaceId || undefined
    }
    if (filter.date) {
      params.startFrom = shanghaiLocalToIso(filter.date, '00:00')
      params.startTo = shanghaiLocalToIso(filter.date, '23:59')
    }
    const fetchPage = isReadonly.value ? pageBoardBookings : pageMyBookings
    const data = await fetchPage(params)
    let list = data.records || []
    // 「节次筛选」：仅保留与所选节次时段重叠的行
    if (filter.periodNo) {
      const p = periods.value.find((item) => item.periodNo === filter.periodNo)
      if (p && filter.date) {
        const segStart = toMin(shanghaiClockOf(shanghaiLocalToIso(filter.date, p.startClock)))
        const segEnd = toMin(shanghaiClockOf(shanghaiLocalToIso(filter.date, p.endClock)))
        list = list.filter((row) => {
          const rStart = toMin(shanghaiClockOf(row.startAt) || '00:00')
          const rEnd = toMin(shanghaiClockOf(row.endAt) || '00:00')
          return rEnd > segStart && rStart < segEnd
        })
      }
    }
    records.value = list
    total.value = Number(data.total) || 0
  } finally {
    listLoading.value = false
  }
}

async function onBookingSubmitted(payloads) {
  if (!payloads || !payloads.length) return
  // 连续段拆分多单：逐段提交，任一失败即中断并提示（已成功的单不重复提交）
  for (let i = 0; i < payloads.length; i++) {
    try {
      await createBooking(payloads[i])
    } catch (e) {
      ElMessage.error(`第 ${i + 1}/${payloads.length} 个预约单提交失败：${e?.message || '请稍后重试'}`)
      return
    }
  }
  ElMessage.success(payloads.length > 1
    ? `${payloads.length} 个预约单已提交，等待管理员核对`
    : '预约已提交，等待管理员核对')
  bookVisible.value = false
  selectedPeriodNos.value = []
  refreshTimeline()
}

async function onSeriesSubmitted() {
  seriesVisible.value = false
  selectedPeriodNos.value = []
  refreshTimeline()
}

function openDetail(row) {
  detailId.value = row.id
  detailVisible.value = true
}

async function onCancel(row) {
  let reason
  try {
    const res = await ElMessageBox.prompt(
      `确定取消预约「${row.labName}」${formatRange(row.startAt, row.endAt)} 吗？`,
      '取消预约',
      {
        inputPlaceholder: '取消原因（选填，≤200 字）',
        inputPattern: /^.{0,200}$/s,
        inputErrorMessage: '原因不能超过 200 字',
        type: 'warning',
        confirmButtonText: '确定取消',
        cancelButtonText: '再想想'
      }
    )
    reason = res.value || ''
  } catch {
    return
  }
  await cancelBooking(row.id, reason || undefined)
  ElMessage.success('预约已取消')
  if (filter.spaceId) {
    refreshTimeline()
  } else {
    loadOverview()
  }
}

onMounted(async () => {
  try {
    periods.value = (await listPeriods()) || []
  } catch {
    periods.value = []
  }
  // 初始为「只选日期」总览模式；学生只读同样加载默认列表（不预选场地）
  loadOverview()
})
</script>

<style scoped>
.tl {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tl__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.tl__title {
  margin: 0 0 4px;
  font-size: 21px;
}

.tl__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.tl__filters {
  padding: 14px 20px;
}

.tl__form {
  display: flex;
  flex-wrap: wrap;
  row-gap: 0;
}

.tl__space {
  width: 240px;
}

.tl__date {
  width: 150px;
}

.tl__period_filter {
  width: 150px;
}

.tl__suggest {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.tl__periods,
.tl__recent,
.tl__list {
  padding: 16px 20px;
}

.tl__periods_head,
.tl__recent_head,
.tl__list_head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.tl__block_title {
  margin: 0;
  font-size: 15px;
  font-weight: var(--fw-semibold, 600);
  color: var(--g1);
}

.tl__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.tl__legend_item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--g3, #6b7280);
}

.tl__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}

.tl__dot--free {
  border: 1.5px solid var(--ok, #17b26a);
  background: rgba(23, 178, 106, .08);
}

.tl__dot--occupied {
  border: 1.5px solid var(--danger, #f04438);
  background: rgba(240, 68, 56, .10);
}

.tl__dot--pending {
  border: 1.5px solid var(--warn, #f79009);
  background: rgba(247, 144, 9, .12);
}

.tl__dot--past {
  border: 1.5px solid var(--g4, #c8c8d0);
  background: transparent;
}

.tl__period_grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 10px;
}

.period_card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 10px 12px;
  border-radius: var(--r-md, 14px);
  background: var(--surface, #fff);
  border: 1.5px solid var(--hair-strong, #e5e7eb);
  cursor: pointer;
  text-align: left;
  transition: transform var(--dur-fast, .16s) var(--ease, ease), box-shadow var(--dur-fast, .16s) var(--ease, ease);
}

.period_card:disabled {
  cursor: not-allowed;
  opacity: .75;
}

.period_card--free {
  border-color: var(--ok, #17b26a);
}

.period_card--free:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px -4px rgba(23, 178, 106, .35);
}

.period_card--occupied {
  border-color: var(--danger, #f04438);
}

.period_card--pending {
  border-color: var(--warn, #f79009);
}

.period_card--past {
  border-color: var(--g4, #d1d5db);
  opacity: .6;
}

.period_card__no {
  font-size: 13px;
  color: var(--g1);
}

.period_card__clock {
  font-size: 12px;
  color: var(--g2, #454b57);
  font-variant-numeric: tabular-nums;
}

.period_card__state {
  font-size: 11px;
  color: var(--g3, #6b7280);
}

.period_card--selected {
  outline: 2px solid var(--brand, #3f86ff);
  outline-offset: 1px;
  background: var(--brand-a08);
}

.period_card--dim {
  opacity: .35;
}

.tl__periods_empty,
.tl__recent_empty {
  grid-column: 1 / -1;
  margin: 24px 0;
  text-align: center;
}

.tl__selection {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.tl__recent_main {
  font-weight: var(--fw-medium, 500);
  color: var(--g1);
}

.tl__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .tl__head {
    flex-direction: column;
  }

  .tl__actions {
    width: 100%;
  }

  .tl__actions .el-button {
    flex: 1;
    margin-left: 0;
  }

  .tl__space,
  .tl__date,
  .tl__period_filter {
    width: 100%;
  }
}
</style>