<template>
  <div class="tl">
    <!-- 页头 -->
    <div class="tl__head">
      <div>
        <h2 class="t-title tl__title">
          {{ isReadonly ? '预约情况' : '场地预约' }}
        </h2>
        <p class="t-sub">
          {{ isReadonly
            ? '只读查看预约情况、批准/驳回结果与驳回原因'
            : '选日期按节次矩阵查看空闲/占用 · 点选空闲格发起预约，可提交批量周期预约' }}
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

    <!-- 节次视图：日矩阵（节次×场地）/ 周日历（节次×星期，教师端） -->
    <div class="card tl__periods">
      <div class="tl__periods_head">
        <h3 class="tl__block_title">
          {{ viewMode === 'week' ? '场地周占用日历' : `${filter.date || today} 节次矩阵` }}
        </h3>
        <div class="tl__view_bar">
          <el-radio-group
            v-model="viewMode"
            size="small"
            @change="onViewModeChange"
          >
            <el-radio-button value="day">
              日矩阵
            </el-radio-button>
            <el-radio-button
              v-if="!isReadonly"
              value="week"
            >
              周日历
            </el-radio-button>
          </el-radio-group>
          <div class="tl__legend">
            <span class="tl__legend_item"><i class="tl__dot tl__dot--free" />空闲可点选</span>
            <span class="tl__legend_item"><i class="tl__dot tl__dot--occupied" />已占用</span>
            <span class="tl__legend_item"><i class="tl__dot tl__dot--pending" />下节课即将占用</span>
            <span class="tl__legend_item"><i class="tl__dot tl__dot--past" />已过去</span>
          </div>
        </div>
      </div>
      <PeriodMatrixDay
        v-if="viewMode === 'day'"
        :periods="periods"
        :labs="matrixLabs"
        :date="filter.date"
        :today="today"
        :readonly="isReadonly"
        :active-space-id="filter.spaceId"
        :selected-range="selectedRange"
        :loading="matrixLoading"
        :now-tick="nowTick"
        :active-period-no="filter.periodNo"
        @pick="onMatrixPick"
      />
      <PeriodWeekCalendar
        v-else
        :periods="periods"
        :space-id="filter.spaceId"
        :anchor-date="filter.date || today"
        :now-tick="nowTick"
        @switch-day="onSwitchDay"
      />
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
import PeriodMatrixDay from './PeriodMatrixDay.vue'
import PeriodWeekCalendar from './PeriodWeekCalendar.vue'
import { BOOKING_STATUS_MAP, RUNTIME_PHASE_MAP, buildPeriodCellMap } from './booking.constants'
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

// 四态派生时钟：每分钟自动重算，实现状态随课程时间节点自动流转
const nowTick = ref(Date.now())
let nowTimer = null

const bookVisible = ref(false)
const seriesVisible = ref(false)
const detailVisible = ref(false)
const detailId = ref('')

// 顶部筛选：场地 Typeahead + 日期 + 节次筛选
// date 初始为空：日期必须由用户显式选择（空→有值必触发 @change），
// 避免「初始已填今天、重选同一天不触发 change」导致总览永远不加载
const filter = reactive({
  keyword: '',
  spaceId: '',
  date: '',
  periodNo: null
})

// 节次点选区间（起始/结束节次号；全空闲才允许成段选择）
const selectedRange = ref([])

// 节次视图模式：day=日矩阵（节次×场地，师生均可用）；week=周日历（节次×星期，教师端）
const viewMode = ref('day')
// 日矩阵数据：全天窗口的场地×占用槽（与下方总览的节次窗口数据分离，互不干扰）
const matrixLabs = ref([])
const matrixLoading = ref(false)
let matrixLoadedDate = ''
let matrixSeq = 0

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
  selectedRange.value = []
  loadMatrix()
  if (filter.spaceId) {
    refreshTimeline()
  }
}

function disablePast(date) {
  const [y, m, d] = today.split('-').map(Number)
  return date.getTime() < Date.UTC(y, m - 1, d)
}

/** 日矩阵数据加载：全天窗口（矩阵需全部节次行的占用槽，与总览节次窗口分离） */
async function loadMatrix() {
  if (!filter.date) {
    matrixLabs.value = []
    return
  }
  if (matrixLoadedDate === filter.date && matrixLabs.value.length) return
  const seq = ++matrixSeq
  matrixLoading.value = true
  try {
    const useMock = isOccupancyMockEnabled()
    const list = useMock
      ? buildMockOccupancy({ date: filter.date })
      : await getLabOccupancy({ date: filter.date })
    if (seq !== matrixSeq) return
    matrixLabs.value = list || []
    matrixLoadedDate = filter.date
  } catch {
    if (seq === matrixSeq) matrixLabs.value = []
  } finally {
    if (seq === matrixSeq) matrixLoading.value = false
  }
}

/**
 * 矩阵单元格点选（教师端）：换场地则同步筛选并以该节为起始；
 * 同场地再点完成区间（区间内须全部空闲，口径与子组件 buildPeriodCellMap 同源）。
 */
function onMatrixPick({ lab, periodNo }) {
  if (isReadonly.value) return
  if (String(filter.spaceId) !== String(lab.spaceId)) {
    filter.spaceId = String(lab.spaceId)
    filter.keyword = lab.labName
    if (!spaces.value.find((s) => String(s.id) === String(lab.spaceId))) {
      spaces.value = [
        ...spaces.value,
        { id: lab.spaceId, labName: lab.labName, capacity: lab.capacity, statusName: lab.statusName }
      ]
    }
    selectedRange.value = [periodNo, periodNo]
    refreshTimeline()
    return
  }
  if (!selectedRange.value.length) {
    selectedRange.value = [periodNo, periodNo]
  } else {
    const [start] = selectedRange.value
    const min = Math.min(start, periodNo)
    const max = Math.max(start, periodNo)
    const cellMap = buildPeriodCellMap([lab], periods.value, filter.date, today, nowShanghaiClock())
    const rangeOk = periods.value
      .filter((p) => p.periodNo >= min && p.periodNo <= max)
      .every((p) => cellMap.get(`${lab.spaceId}:${p.periodNo}`)?.state === 'free')
    if (!rangeOk) {
      ElMessage.warning('所选区间包含已占用节次，请重新选择')
      selectedRange.value = [periodNo, periodNo]
    } else {
      selectedRange.value = [min, max]
    }
  }
}

/** 周日历点击某日表头：切回该日的日矩阵视图 */
function onSwitchDay(date) {
  filter.date = date
  viewMode.value = 'day'
  onDateChanged()
}

function onViewModeChange(mode) {
  if (mode === 'week' && !filter.spaceId) {
    ElMessage.warning('周日历展示单个场地的一周占用，请先选择场地')
  }
}

/** 取消节次区间选择 */
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

const canBookSelected = computed(() => !isReadonly.value && !!filter.spaceId && !!filter.date && !!selectedRange.value.length)

function openBook() {
  if (!canBookSelected.value) {
    ElMessage.warning('请先选择场地，并在节次矩阵中点选空闲节次')
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

.tl__view_bar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
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