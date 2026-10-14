<template>
  <div class="my_bookings">
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
        <el-button
          class="my_bookings__toolbar_action"
          :loading="loading"
          @click="load"
        >
          刷新
        </el-button>
      </div>
      <div class="table-scroll-x">
        <el-table
          v-loading="loading"
          :data="records"
        >
          <el-table-column
            prop="documentNo"
            label="单据号"
            min-width="150"
          />
          <el-table-column
            label="设备"
            min-width="160"
          >
            <template #default="{ row }">
              <div class="t-body">
                {{ row.equipmentName }}
              </div>
              <div class="t-hint">
                {{ row.assetCode }}
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="purpose"
            label="借用用途"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            label="指定老师"
            min-width="90"
          >
            <template #default="{ row }">
              {{ row.approvalTeacherName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="借用时间"
            min-width="160"
          >
            <template #default="{ row }">
              <div class="t-body">
                {{ formatSh(row.startAt) }}
              </div>
              <div class="t-hint">
                至 {{ formatSh(row.endAt) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="110"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="enumMeta(row.status, BOOKING_STATUS).pill"
              >
                {{ enumMeta(row.status, BOOKING_STATUS).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="130"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="isPending(row)"
                v-permission="'equipment:booking:cancel'"
                link
                type="danger"
                @click="openCancel(row)"
              >
                撤销
              </el-button>
              <el-button
                link
                type="primary"
                @click="openDetail(row)"
              >
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="my_bookings__pager"
        :total="total"
        layout="prev, pager, next"
        small
        @change="load"
      />
    </div>

    <!-- 撤销弹窗 -->
    <el-dialog
      v-model="cancelVisible"
      title="撤销借用单"
      width="520px"
      destroy-on-close
    >
      <el-alert
        title="仅待审批状态的本人借用单可撤销；撤销后如需借用请重新提交"
        type="info"
        :closable="false"
        class="my_bookings__alert"
      />
      <el-form label-width="80px">
        <el-form-item label="撤销原因">
          <el-input
            v-model="cancelReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="cancelSaving"
          @click="confirmCancel"
        >
          确认撤销
        </el-button>
      </template>
    </el-dialog>

    <booking-detail-dialog
      v-model="detailVisible"
      :booking="activeRow"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { cancelBooking, pageBookings } from '@/api/equipment'
import { formatSh } from '@/utils/datetime'
import { BOOKING_STATUS, BOOKING_STATUS_OPTIONS, enumMeta } from '@/utils/enums'
import BookingDetailDialog from '@/components/BookingDetailDialog.vue'

const query = reactive({ pageNum: 1, pageSize: 10, status: '' })
const records = ref([])
const total = ref(0)
const loading = ref(false)

const PENDING_STATUSES = ['PENDING_TEACHER', 'PENDING_ADMIN']

function isPending(row) {
  return PENDING_STATUSES.includes(row.status)
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize, scope: 'MINE' }
    if (query.status) {
      params.status = query.status
    }
    const data = await pageBookings(params)
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

/* ---------------- 撤销 ---------------- */

const cancelVisible = ref(false)
const cancelSaving = ref(false)
const cancelReason = ref('')
const activeRow = ref(null)

function openCancel(row) {
  activeRow.value = row
  cancelReason.value = ''
  cancelVisible.value = true
}

async function confirmCancel() {
  if (!activeRow.value) return
  cancelSaving.value = true
  try {
    await cancelBooking(activeRow.value.id, { reason: cancelReason.value.trim() || null })
    ElMessage.success('已撤销')
    cancelVisible.value = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    cancelSaving.value = false
  }
}

/* ---------------- 详情 ---------------- */

const detailVisible = ref(false)

function openDetail(row) {
  activeRow.value = row
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.my_bookings {
  padding: 14px;
}

.my_bookings__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.my_bookings__filter {
  width: 160px;
}

/* 提高选择器特异性以覆盖 .el-button + .el-button 的相邻 margin */
.my_bookings__toolbar .my_bookings__toolbar_action {
  margin-left: auto;
}

.my_bookings__pager {
  margin-top: 12px;
  justify-content: center;
}

.my_bookings__alert {
  margin-bottom: 16px;
}
</style>
