<template>
  <div class="page">
    <el-alert
      title="待我初审的学生物料申领：逐行确认批准量（不超过申请量），通过后转管理员终审；终审批准后学生方可领用"
      type="info"
      :closable="false"
      show-icon
      class="mb12"
    />

    <el-table
      v-loading="pager.loading"
      :data="pager.rows"
      border
    >
      <el-table-column
        prop="id"
        label="申领单号"
        width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="申请人"
        width="110"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ displayName(row.operatorName, row.operatorId) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="purpose"
        label="用途"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="登记时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="180"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openDetail(row.id)"
          >
            详情
          </el-button>
          <el-button
            link
            type="success"
            @click="openApprove(row)"
          >
            通过
          </el-button>
          <el-button
            link
            type="danger"
            @click="doReject(row)"
          >
            驳回
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 申领详情（只读） -->
    <el-dialog
      v-model="detailVisible"
      title="申领单详情"
      width="960px"
    >
      <el-descriptions
        v-if="detail"
        :column="3"
        border
      >
        <el-descriptions-item label="申领单号">
          {{ detail.id }}
        </el-descriptions-item>
        <el-descriptions-item label="申请人">
          {{ displayName(detail.operatorName, detail.operatorId) }}
        </el-descriptions-item>
        <el-descriptions-item label="登记时间">
          {{ fmtShanghai(detail.createTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="参与人数">
          {{ detail.participantCount ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="课时数">
          {{ detail.periodCount ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="用途">
          {{ detail.purpose || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table
        v-if="detail"
        :data="detail.lines"
        border
        class="mt8"
      >
        <el-table-column
          label="物料"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.skuName }}（{{ row.baseUnit }}）
          </template>
        </el-table-column>
        <el-table-column
          label="申请量"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.requestedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="超定额"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.requiresExtraApproval"
              type="danger"
              size="small"
            >
              需签批
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="双签"
          width="70"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.requiresDualSign"
              type="warning"
              size="small"
            >
              双签
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 初审通过（逐行批准量） -->
    <el-dialog
      v-model="approveVisible"
      title="初审通过（草稿 → 待管理员终审）"
      width="820px"
    >
      <div class="muted small mb8">
        逐行填写批准量（不超过申请量，默认按申请量批准）；通过后转管理员终审。
      </div>
      <el-table
        v-loading="approveLoading"
        :data="approveLines"
        border
      >
        <el-table-column
          label="物料"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.skuName }}（{{ row.baseUnit }}）
          </template>
        </el-table-column>
        <el-table-column
          label="申请量"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.requestedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="批准量"
          width="170"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.approvedQty"
              :min="0"
              :max="Number(row.requestedQty)"
              :step="1"
              :precision="3"
              controls-position="right"
            />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="approveVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitApprove"
        >
          通过并交终审
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getRequest,
  pageRequests,
  teacherApproveRequest,
  teacherRejectRequest
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { fmtShanghai, formatQty } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'

/**
 * 物料申领初审（教师端）：归属本人（审批教师）、状态 DRAFT 的待办分页。
 * 数据范围由后端收敛（无终审权仅见归属本人的单）；通过/驳回即老师审核动作。
 */
const pager = usePagedList((params) =>
  pageRequests({ ...params, status: 'DRAFT' })
)

const detailVisible = ref(false)
const detail = ref(null)

const approveVisible = ref(false)
const approveLoading = ref(false)
const submitting = ref(false)
const approveRequestId = ref(null)
const approveLines = ref([])

function displayName(name, id) {
  return name || id || '-'
}

async function openDetail(id) {
  try {
    detail.value = await getRequest(id)
    detailVisible.value = true
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

async function openApprove(row) {
  approveRequestId.value = row.id
  approveVisible.value = true
  approveLoading.value = true
  try {
    const d = await getRequest(row.id)
    approveLines.value = (d?.lines ?? []).map((l) => ({
      ...l,
      approvedQty: l.requestedQty
    }))
  } catch {
    approveLines.value = []
    approveVisible.value = false
  } finally {
    approveLoading.value = false
  }
}

async function submitApprove() {
  for (const line of approveLines.value) {
    if (line.approvedQty === null || line.approvedQty <= 0) {
      ElMessage.warning('批准量必须大于 0')
      return
    }
    if (line.approvedQty > Number(line.requestedQty)) {
      ElMessage.warning(`「${line.skuName}」批准量不能超过申请量`)
      return
    }
  }
  submitting.value = true
  try {
    await teacherApproveRequest(approveRequestId.value, {
      lines: approveLines.value.map((l) => ({ lineId: l.id, approvedQty: l.approvedQty }))
    })
    ElMessage.success('已通过，转管理员终审')
    approveVisible.value = false
    pager.load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function doReject(row) {
  const ok = await confirmAction('确认驳回该申领单？驳回后学生需重新提交。', '驳回确认')
  if (!ok) return
  try {
    await teacherRejectRequest(row.id)
    ElMessage.success('已驳回')
    pager.load()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

onMounted(pager.load)
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.mt8 {
  margin-top: 8px;
}

.mb8 {
  margin-bottom: 8px;
}
</style>
