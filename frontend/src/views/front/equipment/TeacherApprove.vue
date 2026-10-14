<template>
  <div class="teacher_approve">
    <div class="card">
      <div class="teacher_approve__toolbar">
        <span class="t-sub">学生提交的借用单须经指定老师审批通过后，再由管理员审批发放</span>
        <el-button
          class="teacher_approve__toolbar_action"
          :loading="loading"
          @click="search"
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
            prop="applicantName"
            label="申请人"
            min-width="90"
          />
          <el-table-column
            prop="purpose"
            label="借用用途"
            min-width="150"
            show-overflow-tooltip
          />
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
            label="申请时间"
            min-width="150"
          >
            <template #default="{ row }">
              {{ formatSh(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="100"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-permission="'equipment:booking:teacher_approve'"
                link
                type="primary"
                @click="openApprove(row)"
              >
                审批
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!loading && !records.length"
          description="暂无待审批的借用申请"
        />
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="teacher_approve__pager"
        :total="total"
        layout="prev, pager, next"
        small
        @change="load"
      />
    </div>

    <booking-approve-dialog
      v-model="approveVisible"
      action="teacher"
      :booking="activeRow"
      @success="load"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { pageBookings } from '@/api/equipment'
import { formatSh } from '@/utils/datetime'
import BookingApproveDialog from '@/components/BookingApproveDialog.vue'

const query = reactive({ pageNum: 1, pageSize: 10 })
const records = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await pageBookings({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      scope: 'PENDING'
    })
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

const approveVisible = ref(false)
const activeRow = ref(null)

function openApprove(row) {
  activeRow.value = row
  approveVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.teacher_approve {
  padding: 14px;
}

.teacher_approve__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

/* 提高选择器特异性以覆盖 .el-button + .el-button 的相邻 margin */
.teacher_approve__toolbar .teacher_approve__toolbar_action {
  margin-left: auto;
  flex-shrink: 0;
}

.teacher_approve__pager {
  margin-top: 12px;
  justify-content: center;
}
</style>
