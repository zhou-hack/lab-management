<template>
  <div class="bk_approval">
    <!-- 待办概览 -->
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

    <!-- 待审批列表 -->
    <div class="card bk_approval__table_card">
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
            <span class="bk_approval__space">{{ row.spaceName || row.spaceId }}</span>
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
          label="提交时间"
          min-width="140"
        >
          <template #default="{ row }">
            {{ formatInstant(row.createTime) }}
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
              type="success"
              @click="onApprove(row)"
            >
              批准
            </el-button>
            <el-button
              link
              type="danger"
              @click="onReject(row)"
            >
              驳回
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="t-hint">当前没有待审批的预约申请</span>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageAllBookings, approveBooking, rejectBooking } from '@/api/booking'
import { formatInstant, formatRange } from '@/utils/datetime'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const pendingCount = ref(0)

// 审批页固定只看待审批（PENDING）申请
const query = reactive({
  pageNum: 1,
  pageSize: 10
})

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

async function onApprove(row) {
  try {
    await ElMessageBox.confirm(
      `批准「${row.applicantName || row.applicantId}」预约 ${row.spaceName} ${formatRange(row.startAt, row.endAt)} 吗？批准后该时段对其他人关闭。`,
      '批准确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await approveBooking(row.id)
  ElMessage.success('已批准，时段已占用')
  // 当前页删空且不在首页时回退一页
  if (records.value.length === 1 && query.pageNum > 1) {
    query.pageNum -= 1
  }
  loadData()
}

async function onReject(row) {
  let reason
  try {
    const res = await ElMessageBox.prompt(
      '驳回后将通知申请人，原因必填（≤200 字）',
      `驳回「${row.spaceName}」预约`,
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
  if (records.value.length === 1 && query.pageNum > 1) {
    query.pageNum -= 1
  }
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.bk_approval {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bk_approval__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
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

.bk_approval__space {
  font-weight: var(--fw-medium, 500);
}

.bk_approval__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
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
