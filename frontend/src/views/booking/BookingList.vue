<template>
  <div class="my_booking">
    <!-- 工具栏：筛选 + 发起预约 -->
    <div class="card my_booking__filters">
      <el-form
        :inline="true"
        :model="query"
        class="my_booking__form"
      >
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            class="my_booking__select"
          >
            <el-option
              v-for="s in BOOKING_STATUS"
              :key="s.value"
              :label="s.label"
              :value="s.value"
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
            class="my_booking__daterange"
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

    <!-- 预约列表 -->
    <div class="card my_booking__table_card">
      <div class="my_booking__toolbar">
        <div>
          <h2 class="t-title my_booking__title">
            我的预约
          </h2>
          <p class="t-sub">
            时段按上海本地时间展示 · 提交后等待管理员审批，批准后占用生效
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
          label="场地"
          min-width="150"
        >
          <template #default="{ row }">
            <span class="my_booking__space">{{ row.labName || row.spaceId }}</span>
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
          min-width="180"
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
          width="90"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'APPROVED'"
              v-permission="'booking:lab:cancel'"
              link
              type="danger"
              @click="onCancel(row)"
            >
              取消预约
            </el-button>
            <span
              v-else
              class="t-hint"
            >
              —
            </span>
          </template>
        </el-table-column>
        <template #empty>
          <span class="t-hint">暂无预约记录，点击右上角「发起预约」</span>
        </template>
      </el-table>

      <div class="my_booking__pager">
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SpaceBookDialog from './SpaceBookDialog.vue'
import { BOOKING_STATUS, BOOKING_STATUS_MAP, RUNTIME_PHASE_MAP } from './booking.constants'
import { pageMyBookings, createBooking, cancelBooking } from '@/api/booking'
import { formatInstant, formatRange, shanghaiLocalToIso } from '@/utils/datetime'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const bookVisible = ref(false)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: null,
  dateRange: null
})

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined
    }
    // 日期区间（上海本地）→ 带偏移 ISO 时间点提交后端
    const [from, to] = query.dateRange || []
    if (from) params.startFrom = shanghaiLocalToIso(from, '00:00')
    if (to) params.startTo = shanghaiLocalToIso(to, '23:59')
    const data = await pageMyBookings(params)
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
  query.dateRange = null
  query.pageNum = 1
  loadData()
}

async function onSubmitBooking(payload) {
  await createBooking(payload)
  ElMessage.success('预约已提交，等待审批')
  bookVisible.value = false
  query.pageNum = 1
  loadData()
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
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.my_booking {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px;
}

.my_booking__form {
  display: flex;
  flex-wrap: wrap;
}

.my_booking__select {
  width: 120px;
}

.my_booking__daterange {
  width: 240px;
}

.my_booking__table_card {
  padding: 20px;
}

.my_booking__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.my_booking__title {
  margin: 0 0 4px;
  font-size: 18px;
}

.my_booking__space {
  font-weight: var(--fw-medium, 500);
}

.my_booking__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .my_booking__toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .my_booking__select,
  .my_booking__daterange {
    width: 100%;
  }
}
</style>
