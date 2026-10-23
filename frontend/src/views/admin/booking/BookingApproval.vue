<template>
  <div class="bk_approval">
    <el-tabs
      v-model="activeTab"
      class="bk_approval__tabs"
    >
      <!-- 单次预约审批 -->
      <el-tab-pane
        label="单次预约"
        name="single"
      >
        <div class="card bk_approval__summary">
          <div class="bk_approval__summary_main">
            <h2 class="t-title bk_approval__title">
              预约审批
            </h2>
            <p class="t-sub">
              逐单审批待处理申请 · 批准后时段立即占用，与其他申请互斥；驳回须填写原因
            </p>
          </div>
          <div class="bk_approval__pending">
            <p class="bk_approval__pending_num">
              {{ pendingCount }}
            </p>
            <p class="bk_approval__pending_label">
              待审批
            </p>
          </div>
        </div>

        <div class="card bk_approval__table_card">
          <div class="bk_approval__toolbar">
            <el-button
              v-permission="'booking:lab:approve'"
              type="success"
              :disabled="!selectedRows.length"
              :loading="batchApproving"
              @click="onBatchApprove"
            >
              批量批准{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
            </el-button>
            <span class="t-hint">勾选多条待审批申请后可一次性批准，冲突单将逐条反馈原因</span>
          </div>
          <el-table
            v-loading="loading"
            :data="records"
            class="table-scroll-x"
            row-key="id"
            @selection-change="(rows) => (selectedRows = rows)"
          >
            <el-table-column
              type="selection"
              width="46"
            />
            <el-table-column
              label="申请人"
              min-width="100"
            >
              <template #default="{ row }">
                {{ row.applicantName || row.applicantId }}
              </template>
            </el-table-column>
            <el-table-column
              label="场地"
              min-width="190"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span class="bk_approval__space">{{ row.labName || row.spaceId }}</span>
              </template>
            </el-table-column>
            <el-table-column
              label="课程 / 班级"
              min-width="150"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ [row.courseName, row.className].filter(Boolean).join(' · ') || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              label="使用时段"
              min-width="180"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ formatRange(row.startAt, row.endAt) }}
              </template>
            </el-table-column>
            <el-table-column
              prop="purpose"
              label="事由"
              min-width="160"
              show-overflow-tooltip
            />
            <el-table-column
              prop="participantCount"
              label="人数"
              width="70"
              align="right"
            />
            <el-table-column
              label="操作"
              width="150"
              fixed="right"
            >
              <template #default="{ row }">
                <el-button
                  v-permission="'booking:lab:approve'"
                  link
                  type="success"
                  @click="onApprove(row)"
                >
                  批准
                </el-button>
                <el-button
                  v-permission="'booking:lab:approve'"
                  link
                  type="danger"
                  @click="onReject(row)"
                >
                  驳回
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <span class="t-hint">当前没有待审批的单次预约申请</span>
            </template>
          </el-table>

          <div
            v-if="total > query.pageSize"
            class="bk_approval__pager"
          >
            <el-pagination
              v-model:current-page="query.pageNum"
              v-model:page-size="query.pageSize"
              :total="total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="loadData"
              @current-change="loadData"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- 周期预约审批 -->
      <el-tab-pane
        label="周期预约"
        name="series"
      >
        <div class="card bk_approval__table_card">
          <el-table
            v-loading="seriesLoading"
            :data="seriesList"
            class="table-scroll-x"
            row-key="id"
          >
            <el-table-column
              label="申请人"
              min-width="100"
            >
              <template #default="{ row }">
                {{ row.applicantName || row.applicantId }}
              </template>
            </el-table-column>
            <el-table-column
              label="场地"
              min-width="190"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span class="bk_approval__space">{{ row.labName || row.spaceId }}</span>
              </template>
            </el-table-column>
            <el-table-column
              label="课程 / 班级"
              min-width="140"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ [row.courseName, row.className].filter(Boolean).join(' · ') || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              label="星期 + 节次"
              min-width="150"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.weekdayName }} {{ row.startPeriodName }} ~ {{ row.endPeriodName }}
              </template>
            </el-table-column>
            <el-table-column
              label="使用周期"
              min-width="220"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.cycleStartDate }} ~ {{ row.cycleEndDate }}
                <span class="t-hint">（{{ row.weekCount }} 周）</span>
              </template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="96"
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
                  v-else
                  class="pill"
                  :class="SERIES_STATUS_MAP[row.status]?.pill"
                >
                  {{ SERIES_STATUS_MAP[row.status]?.label || row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              type="expand"
              width="48"
            >
              <template #default="{ row }">
                <div class="bk_approval__series_detail">
                  <el-descriptions
                    :column="2"
                    border
                    size="small"
                  >
                    <el-descriptions-item label="用途">
                      {{ row.purpose || '—' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="参与人数">
                      {{ row.participantCount }} 人
                    </el-descriptions-item>
                    <el-descriptions-item label="上课老师">
                      {{ row.teacherName || '—' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="已生成预约单">
                      {{ row.bookedCount }} 条
                    </el-descriptions-item>
                  </el-descriptions>
                  <p
                    v-if="row.status === 'REJECTED' && row.rejectReason"
                    class="bk_approval__reject t-hint"
                  >
                    驳回原因：{{ row.rejectReason }}
                  </p>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="160"
              fixed="right"
            >
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button
                    v-permission="'booking:lab:approve'"
                    link
                    type="success"
                    @click="onApproveSeries(row)"
                  >
                    批准
                  </el-button>
                  <el-button
                    v-permission="'booking:lab:approve'"
                    link
                    type="danger"
                    @click="onRejectSeries(row)"
                  >
                    驳回
                  </el-button>
                </template>
                <el-button
                  v-else
                  link
                  type="primary"
                  @click="openSeriesDetail(row)"
                >
                  详情
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <span class="t-hint">暂无周期预约申请</span>
            </template>
          </el-table>

          <div
            v-if="seriesTotal > seriesQuery.pageSize"
            class="bk_approval__pager"
          >
            <el-pagination
              v-model:current-page="seriesQuery.pageNum"
              v-model:page-size="seriesQuery.pageSize"
              :total="seriesTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="loadSeries"
              @current-change="loadSeries"
            />
          </div>
        </div>

        <el-drawer
          v-model="seriesDetailVisible"
          title="周期预约详情"
          size="520px"
        >
          <div
            v-if="seriesDetail"
            class="bk_approval__detail"
          >
            <div class="card bk_approval__detail_head">
              <h3 class="t-title">{{ seriesDetail.labName || seriesDetail.spaceId }}</h3>
              <p class="t-sub">
                {{ seriesDetail.weekdayName }} {{ seriesDetail.startPeriodName }} ~
                {{ seriesDetail.endPeriodName }} · {{ seriesDetail.cycleStartDate }} ~
                {{ seriesDetail.cycleEndDate }}（{{ seriesDetail.weekCount }} 周）
              </p>
              <span
                class="pill"
                :class="SERIES_STATUS_MAP[seriesDetail.status]?.pill"
              >
                {{ seriesDetail.statusName || SERIES_STATUS_MAP[seriesDetail.status]?.label }}
              </span>
            </div>
            <el-descriptions
              :column="2"
              border
            >
              <el-descriptions-item label="申请人">
                {{ seriesDetail.applicantName || seriesDetail.applicantId }}
              </el-descriptions-item>
              <el-descriptions-item label="上课老师">
                {{ seriesDetail.teacherName || '—' }}
              </el-descriptions-item>
              <el-descriptions-item
                label="课程"
                :span="1"
              >
                {{ seriesDetail.courseName || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="班级">
                {{ seriesDetail.className || '—' }}
              </el-descriptions-item>
              <el-descriptions-item
                label="用途"
                :span="2"
              >
                {{ seriesDetail.purpose || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="参与人数">
                {{ seriesDetail.participantCount }} 人
              </el-descriptions-item>
              <el-descriptions-item label="已生成预约单">
                {{ (seriesDetail.generated || []).length }} 条
              </el-descriptions-item>
            </el-descriptions>
            <h4 class="bk_approval__section">
              各周生成预约单
            </h4>
            <el-table
              :data="seriesDetail.generated || []"
              size="small"
              class="table-scroll-x"
              row-key="bookingId"
            >
              <el-table-column
                label="日期"
                min-width="110"
              >
                <template #default="{ row }">
                  {{ row.bookingDate }}
                </template>
              </el-table-column>
              <el-table-column
                label="时段"
                min-width="120"
              >
                <template #default="{ row }">
                  {{ row.timeRange }}
                </template>
              </el-table-column>
              <el-table-column label="状态">
                <template #default="{ row }">
                  <span
                    class="pill"
                    :class="BOOKING_STATUS_MAP[row.status]?.pill"
                  >
                    {{ row.statusName || row.status }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-drawer>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageAllBookings, approveBooking, batchApproveBookings, rejectBooking } from '@/api/booking'
import { pageAllSeries, approveSeries, rejectSeries, getSeriesDetail } from '@/api/booking'
import { BOOKING_STATUS_MAP, SERIES_STATUS_MAP } from '@/views/booking/booking.constants'
import { formatRange } from '@/utils/datetime'

const emit = defineEmits(['decided'])

const activeTab = ref('single')

// 单次预约
const loading = ref(false)
const records = ref([])
const total = ref(0)
const pendingCount = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })
const selectedRows = ref([])
const batchApproving = ref(false)

// 周期预约
const seriesLoading = ref(false)
const seriesList = ref([])
const seriesTotal = ref(0)
const seriesQuery = reactive({ pageNum: 1, pageSize: 10 })
const seriesDetailVisible = ref(false)
const seriesDetail = ref(null)

async function loadData() {
  loading.value = true
  try {
    const data = await pageAllBookings({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: 'PENDING'
    })
    records.value = data.records || []
    total.value = Number(data.total) || 0
    pendingCount.value = total.value
  } finally {
    loading.value = false
  }
}

async function loadSeries() {
  seriesLoading.value = true
  try {
    const data = await pageAllSeries({
      pageNum: seriesQuery.pageNum,
      pageSize: seriesQuery.pageSize,
      status: 'PENDING'
    })
    seriesList.value = data.records || []
    seriesTotal.value = Number(data.total) || 0
  } finally {
    seriesLoading.value = false
  }
}

async function onApprove(row) {
  try {
    await ElMessageBox.confirm(
      `批准「${row.applicantName || row.applicantId}」预约 ${row.labName} ${formatRange(row.startAt, row.endAt)} 吗？批准后该时段对其他人关闭。`,
      '批准确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await approveBooking(row.id)
  ElMessage.success('已批准，时段已占用')
  if (records.value.length === 1 && query.pageNum > 1) query.pageNum -= 1
  loadData()
  emit('decided')
}

/** 批量批准：逐单独立审批，成功/失败分档提示（冲突单附原因） */
async function onBatchApprove() {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(
      `批量批准选中的 ${rows.length} 条预约申请吗？批准后对应时段立即占用，与其他申请互斥。`,
      '批量批准确认',
      { type: 'warning', confirmButtonText: `批准 ${rows.length} 条`, cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  batchApproving.value = true
  try {
    const data = await batchApproveBookings(rows.map((r) => r.id))
    const okCount = (data.approvedIds || []).length
    const failed = data.failedItems || []
    if (!failed.length) {
      ElMessage.success(`已批量批准 ${okCount} 条，时段已占用`)
    } else {
      const names = failed
        .slice(0, 3)
        .map((f) => {
          const row = rows.find((r) => String(r.id) === String(f.id))
          return `${row?.labName || f.id}：${f.msg}`
        })
        .join('；')
      ElMessage.warning(
        `批准 ${okCount} 条，${failed.length} 条未通过（${names}${failed.length > 3 ? ' 等' : ''}）`
      )
    }
    query.pageNum = 1
    loadData()
    emit('decided')
  } finally {
    batchApproving.value = false
  }
}

async function onReject(row) {
  let reason
  try {
    const res = await ElMessageBox.prompt(
      '驳回后将通知申请人，原因必填（≤200 字）',
      `驳回「${row.labName}」预约`,
      {
        inputPlaceholder: '驳回原因（必填）',
        inputValidator: (v) => (v && v.trim() ? (v.length <= 200 ? true : '原因不能超过 200 字') : '请填写驳回原因'),
        type: 'warning',
        confirmButtonText: '确定驳回',
        cancelButtonText: '取消'
      }
    )
    reason = res.value.trim()
  } catch {
    return
  }
  await rejectBooking(row.id, reason)
  ElMessage.success('已驳回')
  if (records.value.length === 1 && query.pageNum > 1) query.pageNum -= 1
  loadData()
  emit('decided')
}

async function onApproveSeries(row) {
  try {
    await ElMessageBox.confirm(
      `批准「${row.applicantName || row.applicantId}」周期预约 ${row.labName}（${row.weekdayName} ${row.startPeriodName}~${row.endPeriodName}，${row.weekCount} 周）吗？批准后逐周生成预约单并占用时段。`,
      '周期预约批准确认',
      { type: 'warning', confirmButtonText: '批准并生成占用', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await approveSeries(row.id)
  ElMessage.success('已批准，已逐周生成占用')
  if (seriesList.value.length === 1 && seriesQuery.pageNum > 1) seriesQuery.pageNum -= 1
  loadSeries()
  emit('decided')
}

async function onRejectSeries(row) {
  let reason
  try {
    const res = await ElMessageBox.prompt(
      '驳回周期预约后将整单终止，原因必填（≤200 字）',
      `驳回「${row.labName}」周期预约`,
      {
        inputPlaceholder: '驳回原因（必填）',
        inputValidator: (v) => (v && v.trim() ? (v.length <= 200 ? true : '原因不能超过 200 字') : '请填写驳回原因'),
        type: 'warning',
        confirmButtonText: '确定驳回',
        cancelButtonText: '取消'
      }
    )
    reason = res.value.trim()
  } catch {
    return
  }
  await rejectSeries(row.id, reason)
  ElMessage.success('已驳回')
  if (seriesList.value.length === 1 && seriesQuery.pageNum > 1) seriesQuery.pageNum -= 1
  loadSeries()
  emit('decided')
}

async function openSeriesDetail(row) {
  seriesDetailVisible.value = true
  seriesDetail.value = null
  try {
    seriesDetail.value = await getSeriesDetail(row.id)
  } catch {
    seriesDetail.value = null
  }
}

onMounted(() => {
  loadData()
  loadSeries()
})
</script>

<style scoped>
.bk_approval {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bk_approval__tabs {
  background: transparent;
}

.bk_approval__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  margin-bottom: 14px;
}

.bk_approval__title {
  margin: 0 0 4px;
  font-size: 18px;
}

.bk_approval__pending {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 84px;
  padding: 8px 16px;
  background: var(--warn-bg, #fdf6ec);
  border-radius: 12px;
}

.bk_approval__pending_num {
  margin: 0;
  font-size: 30px;
  font-weight: var(--fw-semibold, 600);
  color: var(--warn, #d97706);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.bk_approval__pending_label {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--g3, #6b7280);
}

.bk_approval__table_card {
  padding: 20px;
}

.bk_approval__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.bk_approval__space {
  display: inline-block;
  max-width: 100%;
  font-weight: var(--fw-medium, 500);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
}

.bk_approval__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.bk_approval__series_detail {
  padding: 8px 16px;
}

.bk_approval__reject {
  margin: 8px 0 0;
}

.bk_approval__detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bk_approval__detail_head {
  padding: 16px 20px;
}

.bk_approval__section {
  margin: 6px 0 0;
  font-size: 14px;
  font-weight: var(--fw-semibold, 600);
  color: var(--g1);
}

@media (max-width: 768px) {
  .bk_approval__summary {
    flex-direction: column;
    align-items: stretch;
  }

  .bk_approval__pending {
    flex-direction: row;
    justify-content: center;
    gap: 10px;
  }
}
</style>