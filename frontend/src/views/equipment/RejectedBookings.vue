<template>
  <div class="page rejected_bookings">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          查看驳回申请
        </h2>
        <p class="t-sub">
          本人被驳回的申请与驳回原因 · 驳回后可按原因修改重新申请
        </p>
      </div>
    </div>

    <div class="card">
      <div
        v-if="loading && !records.length"
        class="rejected_bookings__skeleton"
      >
        <span class="skeleton rejected_bookings__sk_row" />
        <span class="skeleton rejected_bookings__sk_row" />
        <span class="skeleton rejected_bookings__sk_row" />
        <span class="skeleton rejected_bookings__sk_row" />
        <span class="skeleton rejected_bookings__sk_row" />
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
                {{ formatSh(row.startAt) }}
              </div>
              <div class="booking_time booking_time--to">
                ~ {{ formatSh(row.endAt) }}
              </div>
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
            label="驳回原因"
            min-width="200"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span :class="row.rejectReason ? '' : 't-hint'">
                {{ row.rejectReason || '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="驳回时间"
            width="140"
          >
            <template #default="{ row }">
              {{ row.rejectedAt ? formatSh(row.rejectedAt) : '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="90"
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
          <CircleCheck />
        </el-icon>
        暂无被驳回的申请
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="rejected_bookings__pager"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </div>

    <!-- 驳回详情 -->
    <el-dialog
      v-model="detail.visible"
      title="申请详情"
      width="560px"
      destroy-on-close
    >
      <div
        v-if="detail.row"
        class="rejected_bookings__detail"
      >
        <div class="rejected_bookings__alert">
          <span class="rejected_bookings__alert_title">驳回原因</span>
          <span class="rejected_bookings__alert_body">
            {{ detail.row.rejectReason || '—' }}
          </span>
        </div>
        <el-descriptions
          :column="1"
          border
        >
          <el-descriptions-item label="单据编号">
            {{ detail.row.documentNo }}
          </el-descriptions-item>
          <el-descriptions-item label="设备">
            {{ detail.row.equipmentName }}（{{ detail.row.assetCode }}）
          </el-descriptions-item>
          <el-descriptions-item label="型号码">
            {{ detail.row.modelCode || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="使用时段">
            {{ formatSh(detail.row.startAt) }} ~ {{ formatSh(detail.row.endAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="借用用途">
            {{ detail.row.purpose }}
          </el-descriptions-item>
          <el-descriptions-item label="审批教师">
            {{ detail.row.approvalTeacherName || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatSh(detail.row.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="驳回时间">
            {{ detail.row.rejectedAt ? formatSh(detail.row.rejectedAt) : '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detail.visible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { CircleCheck } from '@element-plus/icons-vue'
import { pageMyBookings } from '@/api/equipment'
import { formatSh } from '@/utils/datetime'

/**
 * 查看驳回申请（学生端独立菜单，2026-10-22）：
 * 本人被驳回（REJECTED）的申请分页列表 + 驳回原因/时间；
 * 「详情」弹窗展示完整申请信息与驳回原因。复用本人申请查询接口，状态固定 REJECTED。
 */
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: 'REJECTED'
})
const records = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await pageMyBookings(query)
    records.value = data.records || []
    total.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ---------------- 详情 ---------------- */

const detail = reactive({ visible: false, row: null })

function openDetail(row) {
  detail.row = row
  detail.visible = true
}

onMounted(load)
</script>

<style scoped>
.booking_time {
  line-height: 1.5;
  white-space: nowrap;
}

.booking_time--to {
  color: var(--g3);
  font-size: 12px;
}

/* 加载骨架：模拟表格行位（规范 7.8） */
.rejected_bookings__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 2px 10px;
}

.rejected_bookings__sk_row {
  height: 36px;
  border-radius: var(--r-sm);
}

.rejected_bookings__pager {
  margin-top: 14px;
  justify-content: flex-end;
}

/* 驳回原因强调块 */
.rejected_bookings__alert {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
  background: var(--danger-bg, #fef0f0);
  border: .5px solid var(--danger, #f56c6c);
  border-radius: var(--r-md);
}

.rejected_bookings__alert_title {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: var(--fw-semibold);
  color: var(--danger, #f56c6c);
}

.rejected_bookings__alert_body {
  font-size: 13px;
  line-height: 1.6;
  color: var(--g1);
}
</style>
