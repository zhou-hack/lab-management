<template>
  <div class="claim_review">
    <div class="card claim_review__filters">
      <el-form
        :inline="true"
        @submit.prevent
      >
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            clearable
            placeholder="全部状态"
            class="claim_review__status"
          >
            <el-option
              v-for="item in statuses"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预约日期">
          <el-date-picker
            v-model="bookingDates"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            @click="search"
          >
            查询
          </el-button>
          <el-button @click="reset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card">
      <div class="claim_review__head">
        <div>
          <h2 class="t-title">
            申领审核与实发
          </h2>
          <p class="t-sub">
            完整申领可见；审核、实发和关闭操作以当前详情 canX 状态及权限为准
          </p>
        </div>
        <el-button
          :loading="loading"
          @click="loadData"
        >
          刷新
        </el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="records"
        row-key="id"
        class="table-scroll-x"
      >
        <el-table-column
          label="申领单"
          min-width="190"
        >
          <template #default="{ row }">
            <div class="claim_review__document">
              <el-button
                link
                type="primary"
                @click="openDetail(row)"
              >
                {{ row.documentNo }}
              </el-button>
              <span class="t-hint">ID {{ row.id }} · v{{ row.revisionNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="申请人"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.applicantName || row.applicantId }}
          </template>
        </el-table-column>
        <el-table-column
          prop="purpose"
          label="用途"
          min-width="170"
          show-overflow-tooltip
        />
        <el-table-column
          label="预约时段"
          min-width="220"
        >
          <template #default="{ row }">
            {{ formatRange(row.bookingStartAt, row.bookingEndAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="归还期限"
          min-width="160"
        >
          <template #default="{ row }">
            {{ formatTime(row.returnDeadline) }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="110"
        >
          <template #default="{ row }">
            <ItemStatusPill :status="row.status" />
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
      <div class="claim_review__pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="search"
          @current-change="loadData"
        />
      </div>
    </div>

    <ClaimDetailDrawer
      v-model="detailVisible"
      :claim="detail"
      :loading="detailLoading"
      @action="onAction"
    />
    <ClaimIssueDialog
      v-model="issueVisible"
      :claim="detail"
      @success="onChanged"
    />

    <el-dialog
      v-model="reviewVisible"
      title="审核前库存检查"
      width="min(900px, 94vw)"
    >
      <div v-loading="checking">
        <template v-if="preview">
          <el-alert
            :title="preview.valid && !preview.shortages?.length ? '库存检查通过，可批准并生成预占' : '存在库存缺口或风险，请逐项核对'"
            :type="preview.valid && !preview.shortages?.length ? 'success' : 'warning'"
            :closable="false"
            show-icon
            class="claim_review__check_alert"
          />
          <StockShortageTable :items="preview.shortages || []" />
          <el-alert
            v-for="warning in preview.warnings || []"
            :key="warning"
            :title="warning"
            type="warning"
            :closable="false"
            class="claim_review__warning"
          />
          <h3 class="claim_review__section">
            逐项申领与可约数量
          </h3>
          <el-table
            :data="preview.lines || []"
            class="table-scroll-x"
            size="small"
          >
            <el-table-column
              prop="materialName"
              label="物料"
              min-width="180"
            />
            <el-table-column
              label="申请"
              width="110"
              align="right"
            >
              <template #default="{ row }">
                <ItemQuantityText
                  :value="row.requestedQuantity"
                  :unit="row.baseUnit"
                />
              </template>
            </el-table-column>
            <el-table-column
              label="总可约"
              width="110"
              align="right"
            >
              <template #default="{ row }">
                <ItemQuantityText
                  :value="row.totalAvailableQuantity"
                  :unit="row.baseUnit"
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="reservationRule"
              label="预占规则"
              min-width="140"
            />
            <el-table-column
              label="预占时段"
              min-width="210"
            >
              <template #default="{ row }">
                {{ formatRange(row.reservationStartAt, row.reservationEndAt) }}
              </template>
            </el-table-column>
          </el-table>
          <h3 class="claim_review__section">
            计划分配
          </h3>
          <el-table
            :data="preview.allocations || []"
            class="table-scroll-x"
            size="small"
          >
            <el-table-column
              prop="materialName"
              label="物料"
              min-width="170"
            />
            <el-table-column
              label="库位"
              min-width="170"
            >
              <template #default="{ row }">
                {{ row.locationName || row.locationId }} · {{ row.locationCode || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              label="计划 / 可约"
              min-width="160"
              align="right"
            >
              <template #default="{ row }">
                <ItemQuantityText :value="row.plannedQuantity" /> / <ItemQuantityText :value="row.availableQuantity" />
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="reviewVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :disabled="!preview?.valid || Boolean(preview?.shortages?.length)"
          :loading="submitting"
          @click="approve"
        >
          批准并生成预占
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="documentsVisible"
      title="申领业务链单据"
      width="min(850px, 94vw)"
    >
      <div v-loading="documentsLoading">
        <p
          v-if="claimDocuments"
          class="t-sub claim_review__chain"
        >
          业务链ID：{{ claimDocuments.businessChainId }}
        </p>
        <el-table
          :data="claimDocuments?.documents || []"
          class="table-scroll-x"
        >
          <el-table-column
            prop="document.documentNo"
            label="单据号"
            min-width="180"
          />
          <el-table-column
            prop="document.id"
            label="库存单据ID"
            min-width="180"
          />
          <el-table-column
            label="类型"
            width="130"
          >
            <template #default="{ row }">
              {{ documentTypeLabel(row.document.documentType) }}
            </template>
          </el-table-column>
          <el-table-column
            label="场景"
            width="160"
          >
            <template #default="{ row }">
              {{ businessSceneLabel(row.document.businessScene) }}
            </template>
          </el-table-column>
          <el-table-column
            label="行 / 核销 / 事件"
            width="150"
            align="center"
          >
            <template #default="{ row }">
              {{ row.lines?.length || 0 }} / {{ row.settlements?.length || 0 }} / {{ row.events?.length || 0 }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import StockShortageTable from '@/components/item/StockShortageTable.vue'
import ClaimDetailDrawer from '../components/ClaimDetailDrawer.vue'
import ClaimIssueDialog from '../components/ClaimIssueDialog.vue'
import {
  approveAdminClaim,
  checkAdminClaim,
  closeAdminClaim,
  getAdminClaim,
  getClaimDocuments,
  pageAdminClaims,
  rejectAdminClaim
} from '@/api/item/workflow'
import { formatInstant, formatRange } from '@/utils/datetime'
import { businessSceneLabel, documentTypeLabel } from '@/views/item/components/claim.constants'

const emit = defineEmits(['changed'])
const statuses = [
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'PARTIALLY_ISSUED', label: '部分实发' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'REJECTED', label: '已驳回' }
]
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })
const bookingDates = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const issueVisible = ref(false)
const reviewVisible = ref(false)
const checking = ref(false)
const submitting = ref(false)
const preview = ref(null)
const documentsVisible = ref(false)
const documentsLoading = ref(false)
const claimDocuments = ref(null)

const formatTime = (value) => formatInstant(value) || '—'

async function loadData() {
  loading.value = true
  try {
    const result = await pageAdminClaims({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      bookingDateFrom: bookingDates.value?.[0] || undefined,
      bookingDateTo: bookingDates.value?.[1] || undefined,
      sortBy: 'createTime',
      sortOrder: 'desc'
    })
    records.value = result?.records || []
    total.value = Number(result?.total || 0)
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  return loadData()
}

function reset() {
  query.status = ''
  bookingDates.value = []
  return search()
}

async function loadDetail(id) {
  detailLoading.value = true
  try {
    detail.value = await getAdminClaim(id)
  } finally {
    detailLoading.value = false
  }
}

function openDetail(row) {
  detail.value = null
  detailVisible.value = true
  return loadDetail(row.id)
}

async function onAction(action, claim) {
  if (action === 'approve') {
    preview.value = null
    reviewVisible.value = true
    checking.value = true
    try {
      preview.value = await checkAdminClaim(claim.id)
    } catch {
      reviewVisible.value = false
    } finally {
      checking.value = false
    }
    return
  }
  if (action === 'reject') {
    let value
    try {
      const result = await ElMessageBox.prompt('驳回原因将记录在申领事件中（最多512字）', '驳回申领', {
        inputType: 'textarea',
        inputPlaceholder: '请输入驳回原因',
        inputValidator: (value) => Boolean(value?.trim()) || '驳回原因不能为空',
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消'
      })
      value = result.value
    } catch {
      return
    }
    try {
      submitting.value = true
      await rejectAdminClaim(claim.id, { revisionNo: claim.revisionNo, reason: value.trim() })
      ElMessage.success('已驳回申领')
      await onChanged()
    } finally {
      submitting.value = false
    }
    return
  }
  if (action === 'close') {
    try {
      await ElMessageBox.confirm(
        claim.abandonRequestedAt ? '该申领已提交废弃申请，确认关闭并释放剩余预占？' : '确认关闭该申领并释放剩余预占？',
        '关闭申领',
        { type: 'warning', confirmButtonText: '确认关闭' }
      )
    } catch {
      return
    }
    await closeAdminClaim(claim.id)
    ElMessage.success('申领已关闭')
    await onChanged()
    return
  }
  if (action === 'issue') {
    issueVisible.value = true
    return
  }
  if (action === 'documents') {
    documentsVisible.value = true
    documentsLoading.value = true
    try {
      claimDocuments.value = await getClaimDocuments(claim.id)
    } finally {
      documentsLoading.value = false
    }
  }
}

async function approve() {
  if (!detail.value || !preview.value) return
  submitting.value = true
  try {
    await approveAdminClaim(detail.value.id, { revisionNo: detail.value.revisionNo })
    ElMessage.success('审核通过，预占已生成')
    reviewVisible.value = false
    await onChanged()
  } finally {
    submitting.value = false
  }
}

async function onChanged() {
  await loadData()
  if (detailVisible.value && detail.value?.id) await loadDetail(detail.value.id)
  emit('changed')
}

onMounted(loadData)
defineExpose({ reload: loadData })
</script>

<style scoped>
.claim_review__filters { margin-bottom: 16px; }
.claim_review__filters :deep(.el-form-item) { margin-bottom: 0; }
.claim_review__status { width: 150px; }
.claim_review__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.claim_review__head h2 { margin: 0 0 5px; }
.claim_review__head p { margin: 0; }
.claim_review__document { display: flex; flex-direction: column; align-items: flex-start; }
.claim_review__pager { display: flex; justify-content: flex-end; margin-top: 16px; overflow-x: auto; }
.claim_review__check_alert { margin-bottom: 12px; }
.claim_review__warning { margin-top: 10px; }
.claim_review__section { margin: 20px 0 10px; font-size: 15px; }
.claim_review__chain { margin-top: 0; }
@media (max-width: 768px) {
  .claim_review__filters :deep(.el-form-item) { margin-bottom: 10px; }
}
</style>
