<template>
  <div class="bk_admin">
    <!-- 统计卡片区 -->
    <div class="bk_admin__stats">
      <div class="card bk_admin__stat">
        <p class="bk_admin__stat_label">
          预约总数
        </p>
        <p class="bk_admin__stat_num bk_admin__stat_num--brand">
          {{ stats.total }}
        </p>
      </div>
      <div class="card bk_admin__stat">
        <p class="bk_admin__stat_label">
          待审批
        </p>
        <p class="bk_admin__stat_num bk_admin__stat_num--warn">
          {{ stats.pending }}
        </p>
      </div>
      <div class="card bk_admin__stat">
        <p class="bk_admin__stat_label">
          已批准
        </p>
        <p class="bk_admin__stat_num bk_admin__stat_num--ok">
          {{ stats.approved }}
        </p>
      </div>
      <div class="card bk_admin__stat">
        <p class="bk_admin__stat_label">
          已驳回 / 已取消
        </p>
        <p class="bk_admin__stat_num bk_admin__stat_num--muted">
          {{ stats.rejected + stats.cancelled }}
        </p>
      </div>
    </div>

    <!-- 筛选区 -->
    <div class="card bk_admin__filters">
      <el-form
        :inline="true"
        :model="query"
        class="bk_admin__form"
      >
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            class="bk_admin__select"
          >
            <el-option
              v-for="s in BOOKING_STATUS"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="场地">
          <el-select
            v-model="query.spaceId"
            placeholder="全部"
            clearable
            filterable
            class="bk_admin__space_select"
          >
            <el-option
              v-for="s in spaceOptions"
              :key="s.id"
              :label="s.labName"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            class="bk_admin__daterange"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            @click="onSearch"
          >
            查询
          </el-button>
          <el-button @click="onReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="card bk_admin__table_card">
      <div class="bk_admin__toolbar">
        <div>
          <h2 class="t-title bk_admin__title">
            全部预约
          </h2>
          <p class="t-sub">
            时段按上海本地时间展示 · 待审批项可批准/驳回，批准即时占用时段
          </p>
        </div>
        <el-button
          v-permission="'booking:lab:create'"
          type="primary"
          @click="bookVisible = true"
        >
          发起预约
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
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
          min-width="140"
        >
          <template #default="{ row }">
            <span class="bk_admin__space">{{ row.labName || row.spaceId }}</span>
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
          min-width="170"
          show-overflow-tooltip
        />
        <el-table-column
          prop="participantCount"
          label="人数"
          width="70"
          align="right"
        />
        <el-table-column
          label="状态"
          width="96"
        >
          <template #default="{ row }">
            <span
              v-if="row.status === 'APPROVED' && row.runtimePhase"
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
          label="提交时间"
          min-width="140"
        >
          <template #default="{ row }">
            {{ formatInstant(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="170"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="onDetail(row)"
            >
              详情
            </el-button>
            <template v-if="row.status === 'PENDING'">
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
          </template>
        </el-table-column>
        <template #empty>
          <span class="t-hint">暂无预约记录</span>
        </template>
      </el-table>

      <div class="bk_admin__pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <SpaceBookDialog
      v-model="bookVisible"
      @close="bookVisible = false"
      @submit="onSubmitBooking"
    />
    <BookingDetailDrawer
      v-model="detailVisible"
      :booking-id="detailId"
      @close="detailVisible = false"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SpaceBookDialog from '@/views/booking/SpaceBookDialog.vue'
import BookingDetailDrawer from './BookingDetailDrawer.vue'
import { BOOKING_STATUS, BOOKING_STATUS_MAP, RUNTIME_PHASE_MAP } from '@/views/booking/booking.constants'
import {
  pageAllBookings,
  getBookingStats,
  createBooking,
  approveBooking,
  rejectBooking
} from '@/api/booking'
import { listSpaceOptions } from '@/api/space'
import { formatInstant, formatRange, shanghaiLocalToIso } from '@/utils/datetime'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const spaceOptions = ref([])
const bookVisible = ref(false)
const detailVisible = ref(false)
const detailId = ref('')
const stats = reactive({ total: 0, pending: 0, approved: 0, rejected: 0, cancelled: 0 })

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: null,
  spaceId: null,
  dateRange: null
})

async function loadStats() {
  try {
    const data = await getBookingStats()
    Object.assign(stats, {
      total: Number(data.total) || 0,
      pending: Number(data.pending) || 0,
      approved: Number(data.approved) || 0,
      rejected: Number(data.rejected) || 0,
      cancelled: Number(data.cancelled) || 0
    })
  } catch {
    // 统计卡片失败不阻塞列表展示
  }
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      spaceId: query.spaceId || undefined
    }
    const [from, to] = query.dateRange || []
    if (from) params.startFrom = shanghaiLocalToIso(from, '00:00')
    if (to) params.startTo = shanghaiLocalToIso(to, '23:59')
    const data = await pageAllBookings(params)
    records.value = data.records || []
    total.value = Number(data.total) || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.status = null
  query.spaceId = null
  query.dateRange = null
  query.pageNum = 1
  loadData()
}

function onDetail(row) {
  detailId.value = row.id
  detailVisible.value = true
}

async function onSubmitBooking(payload) {
  await createBooking(payload)
  ElMessage.success('预约已提交，等待审批')
  bookVisible.value = false
  query.pageNum = 1
  loadData()
  loadStats()
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
  loadData()
  loadStats()
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
  loadData()
  loadStats()
}

onMounted(() => {
  loadData()
  loadStats()
  listSpaceOptions({})
    .then((list) => {
      spaceOptions.value = list || []
    })
    .catch(() => {
      spaceOptions.value = []
    })
})
</script>

<style scoped>
.bk_admin {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bk_admin__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.bk_admin__stat {
  padding: 16px 20px;
}

.bk_admin__stat_label {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--g3, #6b7280);
}

.bk_admin__stat_num {
  margin: 0;
  font-size: 28px;
  font-weight: var(--fw-semibold, 600);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.bk_admin__stat_num--brand {
  color: var(--brand, #3f86ff);
}

.bk_admin__stat_num--ok {
  color: var(--ok, #2e9e5b);
}

.bk_admin__stat_num--warn {
  color: var(--warn, #d97706);
}

.bk_admin__stat_num--muted {
  color: var(--g3, #6b7280);
}

.bk_admin__form {
  display: flex;
  flex-wrap: wrap;
}

.bk_admin__select {
  width: 120px;
}

.bk_admin__space_select {
  width: 160px;
}

.bk_admin__daterange {
  width: 240px;
}

.bk_admin__table_card {
  padding: 20px;
}

.bk_admin__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.bk_admin__title {
  margin: 0 0 4px;
  font-size: 18px;
}

.bk_admin__space {
  font-weight: var(--fw-medium, 500);
}

.bk_admin__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .bk_admin__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .bk_admin__toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .bk_admin__select,
  .bk_admin__space_select,
  .bk_admin__daterange {
    width: 100%;
  }
}
</style>
