<template>
  <div class="page my_bookings">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          我的设备申请
        </h2>
        <p class="t-sub">
          本人使用申请进度与撤销 · 借出前（待审/已批准）均可撤销
        </p>
      </div>
    </div>

    <div class="card">
      <div class="my_bookings__toolbar">
        <el-select
          v-model="query.status"
          class="my_bookings__filter"
          placeholder="全部状态"
          clearable
          @change="search"
        >
          <el-option
            v-for="option in BOOKING_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button @click="search">
          查询
        </el-button>
      </div>
    </div>

    <div class="card">
      <div
        v-if="loading && !records.length"
        class="my_bookings__skeleton"
      >
        <span class="skeleton my_bookings__sk_row" />
        <span class="skeleton my_bookings__sk_row" />
        <span class="skeleton my_bookings__sk_row" />
        <span class="skeleton my_bookings__sk_row" />
        <span class="skeleton my_bookings__sk_row" />
      </div>
      <div
        v-else-if="records.length"
        class="table-scroll-x"
      >
        <el-table
          v-loading="loading"
          :data="records"
        >
          <el-table-column
            prop="documentNo"
            label="单据编号"
            min-width="140"
          />
          <el-table-column
            prop="equipmentName"
            label="设备名称"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="assetCode"
            label="资产编号"
            min-width="110"
          />
          <el-table-column
            label="使用时段"
            min-width="150"
          >
            <template #default="{ row }">
              <div class="booking_time">
                {{ formatInstant(row.startAt) }}
              </div>
              <div class="booking_time booking_time--to">
                ~ {{ formatInstant(row.endAt) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="价值分级"
            width="90"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="enumMeta(row.valueLevelSnapshot, VALUE_LEVEL).pill"
              >
                {{ enumMeta(row.valueLevelSnapshot, VALUE_LEVEL).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="借用用途"
            min-width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.purpose }}
            </template>
          </el-table-column>
          <el-table-column
            label="审批教师"
            width="90"
          >
            <template #default="{ row }">
              {{ row.approvalTeacherName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="110"
          >
            <template #default="{ row }">
              <el-tooltip
                v-if="row.status === 'CANCELLED' && row.cancelReason"
                :content="`撤销原因：${row.cancelReason}`"
                placement="top"
              >
                <span
                  class="pill"
                  :class="enumMeta(row.status, BOOKING_STATUS).pill"
                >
                  {{ enumMeta(row.status, BOOKING_STATUS).label }}
                </span>
              </el-tooltip>
              <span
                v-else
                class="pill"
                :class="enumMeta(row.status, BOOKING_STATUS).pill"
              >
                {{ enumMeta(row.status, BOOKING_STATUS).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="提交时间"
            width="140"
          >
            <template #default="{ row }">
              {{ formatInstant(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="80"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="isCancellable(row)"
                v-permission="'equipment:booking:cancel'"
                link
                type="danger"
                @click="cancel(row)"
              >
                撤销
              </el-button>
              <span
                v-else
                class="t-hint"
              >—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div
        v-else
        class="empty"
      >
        <el-icon
          class="empty__icon"
          :size="40"
        >
          <Tickets />
        </el-icon>
        暂无申请记录，可在「设备目录」提交使用申请
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="my_bookings__pager"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import { cancelBooking, pageMyBookings } from '@/api/equipment'
import { BOOKING_STATUS, BOOKING_STATUS_OPTIONS, VALUE_LEVEL, enumMeta } from '@/utils/enums'
import { formatInstant } from '@/utils/datetime'

/**
 * 我的设备申请（学生/教师用户端）：本人申请分页列表 + 状态筛选 + 借出前撤销。
 */
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: ''
})
const records = ref([])
const total = ref(0)
const loading = ref(false)

/** 借出前（待审/已批准）可撤销 */
function isCancellable(row) {
  return ['PENDING_TEACHER', 'PENDING_ADMIN', 'APPROVED'].includes(row.status)
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.status) {
      params.status = query.status
    }
    const data = await pageMyBookings(params)
    records.value = data.records || []
    total.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

async function cancel(row) {
  let reason
  try {
    const { value } = await ElMessageBox.prompt(
      `撤销「${row.equipmentName}（${row.assetCode}）」的申请？可填写撤销原因（选填）。`,
      '撤销申请',
      {
        type: 'warning',
        inputType: 'textarea',
        inputPlaceholder: '撤销原因（选填，不超过 500 字）',
        inputValidator: (text) => !text || text.length <= 500 || '撤销原因不能超过 500 字',
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消'
      }
    )
    reason = (value || '').trim()
  } catch {
    return
  }
  try {
    await cancelBooking(row.id, { reason: reason || null })
    ElMessage.success('已撤销')
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

onMounted(load)
</script>

<style scoped>
.my_bookings__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.my_bookings__filter {
  width: 160px;
}

/* 时段两行展示：结束行弱化 */
.booking_time {
  line-height: 1.5;
  white-space: nowrap;
}

.booking_time--to {
  color: var(--g3);
  font-size: 12px;
}

/* 加载骨架：模拟表格行位（规范 7.8） */
.my_bookings__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 2px 10px;
}

.my_bookings__sk_row {
  height: 36px;
  border-radius: var(--r-sm);
}

.my_bookings__pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
