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
            ? '只读查看预约情况、批准/驳回结果与驳回原因'
            : '选日期按节次查看空闲/占用 · 点选空闲节次发起预约，可提交批量周期预约' }}
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

    <!-- 节次三色小卡片 -->
    <div class="card tl__periods">
      <div class="tl__periods_head">
        <h3 class="tl__block_title">
          {{ filter.date || today }} 节次安排
        </h3>
        <div class="tl__legend">
          <span class="tl__legend_item"><i class="tl__dot tl__dot--free" />空闲可点选</span>
          <span class="tl__legend_item"><i class="tl__dot tl__dot--occupied" />已占用</span>
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
          :class="[PERIOD_CARD_MAP[card.state].cls, { 'period_card--selected': isInRange(card.periodNo), 'period_card--dim': filter.periodNo && card.periodNo !== filter.periodNo }]"
          :disabled="card.state !== 'free'"
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
          请先选择场地与日期
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
          @click="clearRange"
        >
          取消选择
        </el-button>
      </div>
    </div>

    <!-- 场地占用总览：日期 + 节次窗口内全部场地空闲/占用分档，占用附老师信息 -->
    <div class="card tl__overview">
      <div class="tl__overview_head">
        <div>
          <h3 class="tl__block_title">
            场地占用总览
          </h3>
          <p class="t-hint">
            {{ overviewTip }}
          </p>
        </div>
        <div class="tl__overview_side">
          <div class="tl__legend">
            <span class="tl__legend_item"><i class="tl__dot tl__dot--free" />空闲</span>
            <span class="tl__legend_item"><i class="tl__dot tl__dot--occupied" />已占用</span>
            <span class="tl__legend_item"><i class="tl__dot tl__dot--pending" />下节课预留</span>
            <span class="tl__legend_item"><i class="tl__dot tl__dot--past" />已过时释放</span>
          </div>
          <span
            v-if="overview.length"
            class="t-hint"
          >{{ overviewCountText }}</span>
        </div>
      </div>

      <!-- 4 大板块：区域均衡分组，实验室内教室两类混合，占用/空闲分档展示 -->
      <div
        v-loading="overviewLoading"
        class="tl__sections"
      >
        <template v-if="overview.length">
          <div
            v-for="section in overviewSections"
            :key="section.title"
            class="tl__section"
          >
            <div class="tl__section_head">
              <h4 class="tl__group_title">
                {{ section.title }}
              </h4>
              <span class="t-hint">
                {{ isPeriodMode
                  ? `${section.labs.length} 间空闲`
                  : `${section.counts.free} 空闲 · ${section.counts.reserved} 预留 · ${section.counts.occupied} 占用 · ${section.counts.released} 释放` }}
              </span>
            </div>
            <div class="tl__lab_grid tl__lab_grid--section">
              <button
                v-for="lab in section.labs"
                :key="lab.spaceId"
                type="button"
                class="lab_card"
                :class="[labCardCls(lab), { 'lab_card--active': lab.spaceId === filter.spaceId }]"
                :disabled="!labBookable(lab)"
                @click="onPickLab(lab)"
              >
                <div class="lab_card__top">
                  <b class="lab_card__name">{{ lab.labName }}</b>
                  <span
                    class="pill"
                    :class="labPillCls(lab)"
                  >{{ labStateLabel(lab) }}</span>
                </div>
                <span class="lab_card__meta">
                  {{ lab.labTypeName || '场地' }} · {{ lab.capacity }} 人<template v-if="lab.locationDetail"> · {{ lab.locationDetail }}</template>
                </span>
                <span
                  v-if="labState(lab) === 'occupied'"
                  class="lab_card__teacher"
                >
                  老师：{{ currentSlot(lab)?.teacherName || currentSlot(lab)?.applicantName || '—' }}<template v-if="currentSlot(lab)?.courseName"> · {{ currentSlot(lab).courseName }}</template>
                </span>
                <span
                  v-else-if="labState(lab) === 'reserved'"
                  class="lab_card__reserved_info"
                >
                  下节课：{{ nextSlot(lab)?.teacherName || nextSlot(lab)?.applicantName || '—' }}<template v-if="nextSlot(lab)?.courseName"> · {{ nextSlot(lab).courseName }}</template>
                </span>
                <span
                  v-else-if="labState(lab) === 'released'"
                  class="lab_card__hint"
                >今日课程已结束，点击选为预约场地</span>
                <span
                  v-else-if="labState(lab) === 'free'"
                  class="lab_card__hint"
                >点击选为预约场地</span>
              </button>
              <p
                v-if="!section.labs.length"
                class="t-hint tl__periods_empty"
              >
                本板块暂无匹配场地
              </p>
            </div>
          </div>
        </template>
        <p
          v-if="!overview.length && !overviewLoading"
          class="t-hint tl__periods_empty"
        >
          {{ isPeriodMode ? '该节次暂无空闲场地，可切换其他节次查看' : '请先选择日期，查看当日场地占用情况' }}
        </p>
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
            <span
              v-if="row.className"
              class="t-hint"
            > · {{ row.className }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="使用时段"
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
          label="使用时段"
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
      :preset-period-nos="selectedRange"
      @close="bookVisible = false"
      @submit="onBookingSubmitted"
    />
    <SeriesBookDialog
      v-model="seriesVisible"
      :preset-space-id="filter.spaceId"
      :preset-date="filter.date"
      :preset-period-nos="selectedRange.slice(0, 2)"
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
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useUserStore } from '@/store/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import SpaceBookDialog from './SpaceBookDialog.vue'
import SeriesBookDialog from './SeriesBookDialog.vue'
import MyBookingDetailDrawer from './MyBookingDetailDrawer.vue'
import { BOOKING_STATUS_MAP, RUNTIME_PHASE_MAP, PERIOD_CARD_MAP } from './booking.constants'
import { buildMockOccupancy, isOccupancyMockEnabled } from './occupancy.mock'
import { listSpaceOptions } from '@/api/space'
import {
  getAvailability,
  getLabOccupancy,
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
const isReadonly = computed(() => userStore.userInfo?.userType === 'STUDENT')

const today = todayShanghai()
const toMin = (t) => (Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5)))

const periods = ref([])
const spaces = ref([])
const availability = ref(null)
const recent = ref([])
const records = ref([])
const total = ref(0)

const periodsLoading = ref(false)
const recentLoading = ref(false)
const listLoading = ref(false)

// 场地占用总览：全部分档 + 请求序号防过期响应覆盖
const overview = ref([])
const overviewLoading = ref(false)
let overviewSeq = 0
// 是否已显式点击选择过日期：未操作前总览保持空白，不预加载全量场地
const dateSelected = ref(false)
// 四态派生时钟：每分钟自动重算，实现状态随课程时间节点自动流转
const nowTick = ref(Date.now())
let nowTimer = null
/** 下节课预留窗口（分钟）：下一占用段开始前该时长内标记为预留，不可新预约 */
const RESERVE_MINUTES = 60

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

// 节次点选区间（起始/结束节次号；全空闲才允许成段选择）
const selectedRange = ref([])

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
    const list = await listSpaceOptions({ availableOnly: true, keyword: kw || undefined, limit: 50 })
    spaces.value = list || []
    cb(structuredClone(list || []))
  } catch {
    cb([])
  }
}

function onSelectSpace(item) {
  filter.spaceId = item.id
  filter.keyword = item.labName
  refreshTimeline()
}

function onClearSpace() {
  filter.spaceId = ''
  filter.keyword = ''
  availability.value = null
  recent.value = []
  selectedRange.value = []
}

function onDateChanged() {
  dateSelected.value = true
  console.log('[占用总览] 日期选择变更：', filter.date, '（联动刷新当日场地总览）')
  selectedRange.value = []
  if (filter.spaceId) {
    // 已选场地：refreshTimeline 末尾统一触发 loadOverview，避免重复请求
    refreshTimeline()
  } else {
    loadOverview()
  }
}

function onPeriodFiltered() {
  // 节次筛选：高亮对应卡片；总览切换为该节次空闲场地归类视图
  console.log('[占用总览] 节次筛选变更：periodNo =', filter.periodNo ?? '（已清空，回到全天视图）')
  loadOverview()
}

function disablePast(date) {
  const [y, m, d] = today.split('-').map(Number)
  return date.getTime() < Date.UTC(y, m - 1, d)
}

/** 某节次是否被已批准占用段命中（半开区间重叠） */
function isOccupied(period) {
  const occupied = availability.value?.occupied || []
  return occupied.some((seg) => {
    const s = shanghaiClockOf(seg.startAt)
    const e = shanghaiClockOf(seg.endAt)
    if (!s || !e) return false
    const segStart = toMin(s)
    const segEnd = toMin(e)
    return segEnd > toMin(period.startClock) && segStart < toMin(period.endClock)
  })
}

/** 节次卡片三色派生（前端按当前时间计算，不落库） */
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
    } else if (isOccupied(p)) {
      state = 'occupied'
    } else {
      const next = periods.value[idx + 1]
      state = next && isOccupied(next) ? 'pending' : 'free'
    }
    return { ...p, state }
  })
})

function isInRange(periodNo) {
  if (!selectedRange.value.length) return false
  const [start, end] = selectedRange.value
  return periodNo >= start && periodNo <= end
}

function onPeriodClick(card) {
  if (card.state !== 'free') {
    ElMessage.warning(PERIOD_CARD_MAP[card.state].tip)
    return
  }
  if (!selectedRange.value.length) {
    selectedRange.value = [card.periodNo, card.periodNo]
    return
  }
  const [start] = selectedRange.value
  const min = Math.min(start, card.periodNo)
  const max = Math.max(start, card.periodNo)
  // 成段选择要求区间内全部空闲
  const rangeOk = cards.value
    .filter((c) => c.periodNo >= min && c.periodNo <= max)
    .every((c) => c.state === 'free')
  if (!rangeOk) {
    ElMessage.warning('所选区间包含已占用节次，请重新选择')
    selectedRange.value = [card.periodNo, card.periodNo]
    return
  }
  selectedRange.value = [min, max]
}

/** 取消节次区间选择（仅影响预约点选，不影响场地占用总览） */
function clearRange() {
  selectedRange.value = []
}

const selectionTip = computed(() => {
  if (!selectedRange.value.length) return ''
  const [start, end] = selectedRange.value
  const p = periods.value.filter((item) => item.periodNo >= start && item.periodNo <= end)
  if (!p.length) return ''
  return `${p[0].periodName} ~ ${p[p.length - 1].periodName}（${p[0].startClock} - ${p[p.length - 1].endClock}）`
})

/** 总览窗口推导：显式点选日期后才加载；再选节次则切换为该节次窗口 */
const overviewWindow = computed(() => {
  if (!dateSelected.value || !filter.date) return null
  if (filter.periodNo) {
    const p = periods.value.find((item) => Number(item.periodNo) === Number(filter.periodNo))
    if (!p) return null
    return {
      date: filter.date,
      startPeriodId: p.id,
      endPeriodId: p.id,
      label: `${p.periodName}（${p.startClock} - ${p.endClock}）`
    }
  }
  return { date: filter.date }
})

/** 节次归类模式：已选日期 + 节次 → 仅展示该节次空闲场地 */
const isPeriodMode = computed(() => Boolean(dateSelected.value && filter.date && filter.periodNo))

const overviewTip = computed(() => {
  const w = overviewWindow.value
  if (!w) return '请先选择日期，查看当日场地占用情况'
  return w.label
    ? `${w.date} ${w.label} 空闲场地：按类型 / 区域 / 容量归类，可点选预约`
    : `${w.date} 全天全部场地：空闲可点选预约，已占用展示使用老师`
})

const overviewFreeCount = computed(
  () => overview.value.filter((lab) => labBookable(lab)).length
)

const overviewCountText = computed(() =>
  isPeriodMode.value
    ? `${overviewFreeCount.value} 间空闲场地`
    : `${overviewFreeCount.value} 空闲 / ${overview.value.length} 场地`
)

/** 区域提取：位置或名称中的「X楼/实训楼/教学楼」，归一为分组键 */
function regionOf(lab) {
  const src = `${lab.locationDetail || ''} ${lab.labName || ''}`
  const m = src.match(/(实训楼|教学楼|[A-Z]\s*楼)/i)
  return m ? m[1].replace(/\s+/g, '') : '其他区域'
}

/** 板块内排序：空闲优先 → 类型 → 容量升序（两类资源交错可见） */
function sortSectionLabs(labs) {
  return labs.slice().sort((a, b) =>
    labRank(a) - labRank(b)
    || (a.labTypeName || '').localeCompare(b.labTypeName || '', 'zh')
    || a.capacity - b.capacity)
}

/**
 * 4 大板块构建：按区域成组后贪心装入当前最小板块，
 * 任意场地数量/区域构成下板块规模保持均衡，两类资源自然混合。
 */
function buildSections(labs) {
  const byRegion = new Map()
  for (const lab of labs) {
    const region = regionOf(lab)
    if (!byRegion.has(region)) byRegion.set(region, [])
    byRegion.get(region).push(lab)
  }
  const regionGroups = [...byRegion.entries()]
    .map(([region, items]) => ({ region, items }))
    .sort((a, b) => b.items.length - a.items.length)

  const sections = [0, 1, 2, 3].map((i) => ({ key: `板块${['一', '二', '三', '四'][i]}`, regions: [], labs: [] }))
  for (const group of regionGroups) {
    const target = sections.reduce((min, s) => (s.labs.length < min.labs.length ? s : min), sections[0])
    target.regions.push(group.region)
    target.labs.push(...group.items)
  }
  return sections.map((s) => {
    const regions = s.regions.slice().sort((a, b) => a.localeCompare(b, 'zh'))
    const title = `${s.key}（${regions.length > 3 ? `${regions.slice(0, 3).join(' · ')} 等` : regions.join(' · ') || '暂无区域'}）`
    const sorted = sortSectionLabs(s.labs)
    const countOf = (target) => sorted.filter((l) => labState(l) === target).length
    return {
      title,
      labs: sorted,
      counts: {
        free: countOf('free'),
        reserved: countOf('reserved'),
        occupied: countOf('occupied'),
        released: countOf('released'),
        off: countOf('off')
      }
    }
  })
}

/** 总览 4 板块：全天模式装全部场地；节次模式仅装该节次空闲场地 */
const overviewSections = computed(() => {
  const source = isPeriodMode.value
    ? overview.value.filter((lab) => !lab.occupied && lab.status === 'AVAILABLE')
    : overview.value
  return buildSections(source)
})

async function loadOverview() {
  const w = overviewWindow.value
  if (!w) {
    console.log('[占用总览] 未显式选择日期，保持空白初始态，不发起请求')
    overview.value = []
    return
  }
  const seq = ++overviewSeq
  overviewLoading.value = true
  try {
    const params = { date: w.date }
    if (w.startPeriodId) {
      params.startPeriodId = w.startPeriodId
      params.endPeriodId = w.endPeriodId
    }
    const useMock = isOccupancyMockEnabled()
    console.log('[占用总览] 触发加载：日期=', w.date, w.label ? `节次=${w.label}` : '（全天窗口）', ' 请求参数=', params, ' mock=', useMock)
    const list = useMock ? buildMockOccupancy(params) : await getLabOccupancy(params)
    if (seq !== overviewSeq) {
      console.log('[占用总览] 响应已过期（序号', seq, '），丢弃')
      return
    }
    // 分档排序：空闲 → 已占用 → 维护/关闭（节次模式下前端再过滤仅空闲并归类）
    overview.value = (list || []).slice().sort((a, b) => labRank(a) - labRank(b))
    console.log('[占用总览] 加载完成：日期=', w.date, ' 模式=', isPeriodMode.value ? '节次空闲归类' : '全天总览',
      ' 总数=', overview.value.length, ' 空闲=', overviewFreeCount.value,
      isPeriodMode.value ? '' : ' 占用=' + JSON.stringify(overview.value.filter((l) => l.occupied).map((l) => `${l.labName}(${l.teacherName || l.applicantName})`)))
  } catch (e) {
    console.warn('[占用总览] 加载失败：', e)
    if (seq === overviewSeq) overview.value = []
  } finally {
    if (seq === overviewSeq) overviewLoading.value = false
  }
}

/**
 * 场地四态派生（按当前时刻实时计算，不落库）：
 * free=空闲（今日无占用，或下一节课距现在超过预留窗口，可预约）；
 * occupied=已占用（此刻有课，不可预约）；
 * reserved=下节课预留（下一节课 60 分钟内开始，不可新预约）；
 * released=已过时释放（今日课程已全部结束，资源已释放，可预约）；
 * off=维护中/关闭。非今天日期仅有 free/occupied 两态。
 */
function labState(lab) {
  if (lab.status !== 'AVAILABLE') return 'off'
  const slots = lab.slots || []
  if (!slots.length) return 'free'
  if (filter.date !== today) return 'occupied'
  const now = nowTick.value
  const toMs = (iso) => new Date(iso).getTime()
  if (slots.some((s) => toMs(s.startAt) <= now && now < toMs(s.endAt))) return 'occupied'
  const next = slots.find((s) => toMs(s.startAt) > now)
  if (!next) return 'released'
  return toMs(next.startAt) - now <= RESERVE_MINUTES * 60 * 1000 ? 'reserved' : 'free'
}

/** 当前进行/最近一节课（占用展示用）；预留态取下一节课 */
function currentSlot(lab) {
  const slots = lab.slots || []
  if (!slots.length) return null
  const now = nowTick.value
  const toMs = (iso) => new Date(iso).getTime()
  return slots.find((s) => toMs(s.startAt) <= now && now < toMs(s.endAt)) || slots[0]
}

function nextSlot(lab) {
  const now = nowTick.value
  return (lab.slots || []).find((s) => new Date(s.startAt).getTime() > now) || null
}

function labRank(lab) {
  return { free: 0, released: 1, reserved: 2, occupied: 3, off: 4 }[labState(lab)]
}

function labCardCls(lab) {
  return {
    free: 'lab_card--free',
    occupied: 'lab_card--occupied',
    reserved: 'lab_card--reserved',
    released: 'lab_card--released',
    off: 'lab_card--off'
  }[labState(lab)]
}

function labPillCls(lab) {
  return {
    free: 'pill--ok',
    occupied: 'pill--danger',
    reserved: 'pill--warn',
    released: 'pill--muted',
    off: 'pill--muted'
  }[labState(lab)]
}

function labStateLabel(lab) {
  const state = labState(lab)
  if (state === 'off') return lab.statusName || '不可用'
  return { free: '空闲', occupied: '已占用', reserved: '下节课预留', released: '已过时释放' }[state]
}

/** 可预约性：空闲与已过时释放可点选；占用/预留/停用不可 */
function labBookable(lab) {
  const state = labState(lab)
  return state === 'free' || state === 'released'
}

/** 点选可预约场地：带入上方筛选并刷新时间轴 */
function onPickLab(lab) {
  if (!labBookable(lab)) return
  filter.spaceId = lab.spaceId
  filter.keyword = lab.labName
  if (!spaces.value.find((s) => s.id === lab.spaceId)) {
    spaces.value = [
      ...spaces.value,
      { id: lab.spaceId, labName: lab.labName, capacity: lab.capacity, statusName: lab.statusName }
    ]
  }
  refreshTimeline()
}

const canBookSelected = computed(() => !isReadonly.value && !!filter.spaceId && !!filter.date && !!selectedRange.value.length)

function openBook() {
  if (!canBookSelected.value) {
    ElMessage.warning('请先选择场地，并在节次卡片中点选空闲节次')
    return
  }
  bookVisible.value = true
}

function openSeries() {
  if (!filter.spaceId) {
    ElMessage.warning('请先在上方选择场地')
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
  loadList()
  loadOverview()
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

async function onBookingSubmitted(payload) {
  if (!payload) return
  await createBooking(payload)
  ElMessage.success('预约已提交，等待管理员核对')
  bookVisible.value = false
  selectedRange.value = []
  refreshTimeline()
}

async function onSeriesSubmitted() {
  seriesVisible.value = false
  selectedRange.value = []
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
  refreshTimeline()
}

onMounted(async () => {
  try {
    periods.value = (await listPeriods()) || []
  } catch {
    periods.value = []
  }
  // 学生只读同样加载默认列表（不预选场地）；总览须先显式点选日期，初始保持空白不预加载
  loadList()
  // 四态自动流转：每 30 秒重算当前时刻派生态（预留/占用/释放随课程节点自动切换）
  nowTimer = setInterval(() => {
    nowTick.value = Date.now()
  }, 30 * 1000)
})

onUnmounted(() => {
  if (nowTimer) {
    clearInterval(nowTimer)
    nowTimer = null
  }
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

.tl__overview {
  padding: 16px 20px;
}

.tl__overview_head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.tl__overview_head .t-hint {
  margin: 4px 0 0;
}

.tl__overview_side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.tl__lab_grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

/* 4 大板块：2×2 等尺寸布局，板块边界清晰、信息密度一致 */
.tl__sections {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  align-items: stretch;
}

.tl__section {
  display: flex;
  flex-direction: column;
  padding: 12px 14px;
  border: 1px solid var(--hair-strong, #e5e7eb);
  border-radius: var(--r-md, 14px);
  background: var(--surface-sunken, #fafbfc);
  min-height: 180px;
}

.tl__section_head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.tl__lab_grid--section {
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  flex: 1;
  align-content: start;
}

.tl__group_title {
  margin: 0;
  font-size: 14px;
  font-weight: var(--fw-semibold, 600);
  color: var(--g1);
}

.tl__group_title .t-hint {
  margin-left: 6px;
  font-weight: var(--fw-regular, 400);
}

@media (max-width: 900px) {
  .tl__sections {
    grid-template-columns: 1fr;
  }
}

.lab_card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px 14px;
  border-radius: var(--r-md, 14px);
  background: var(--surface, #fff);
  border: 1.5px solid var(--hair-strong, #e5e7eb);
  cursor: pointer;
  text-align: left;
  transition: transform var(--dur-fast, .16s) var(--ease, ease), box-shadow var(--dur-fast, .16s) var(--ease, ease);
}

.lab_card:disabled {
  cursor: not-allowed;
}

.lab_card--free {
  border-color: var(--ok, #17b26a);
}

.lab_card--free:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px -4px rgba(23, 178, 106, .35);
}

.lab_card--occupied {
  border-color: var(--danger, #f04438);
  background: rgba(240, 68, 56, .04);
}

.lab_card--reserved {
  border-color: var(--warn, #f79009);
  background: rgba(247, 144, 9, .05);
}

.lab_card--released {
  border-color: var(--g4, #d1d5db);
  background: rgba(107, 114, 128, .04);
}

.lab_card--off {
  border-color: var(--g4, #d1d5db);
  opacity: .65;
}

.lab_card__reserved_info {
  font-size: 12px;
  font-weight: var(--fw-medium, 500);
  color: var(--warn, #f79009);
}

.lab_card--active {
  outline: 2px solid var(--brand, #3f86ff);
  outline-offset: 1px;
}

.lab_card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}

.lab_card__name {
  font-size: 13px;
  color: var(--g1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lab_card__meta {
  font-size: 12px;
  color: var(--g2, #454b57);
}

.lab_card__teacher {
  font-size: 12px;
  font-weight: var(--fw-medium, 500);
  color: var(--danger, #f04438);
}

.lab_card__hint {
  font-size: 11px;
  color: var(--g3, #6b7280);
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