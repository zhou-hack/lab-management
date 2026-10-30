<template>
  <div class="document_center">
    <div class="card document_center__filters">
      <el-form
        :inline="true"
        @submit.prevent
      >
        <el-form-item label="单据号">
          <el-input
            v-model="query.documentNo"
            clearable
            placeholder="精确 / 模糊查询"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="业务链ID">
          <el-input
            v-model="query.businessChainId"
            clearable
            placeholder="输入完整业务链ID"
            class="document_center__chain"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select
            v-model="query.documentType"
            clearable
            placeholder="全部类型"
            class="document_center__select"
          >
            <el-option
              v-for="item in documentTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="场景">
          <el-select
            v-model="query.businessScene"
            clearable
            placeholder="全部场景"
            class="document_center__scene"
          >
            <el-option
              v-for="item in businessScenes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库位">
          <ItemLocationSelect
            v-model="query.locationId"
            class="document_center__location"
          />
        </el-form-item>
        <el-form-item label="发生日期">
          <el-date-picker
            v-model="dates"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
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
      <div class="document_center__head">
        <div>
          <h2 class="t-title">
            出入库与移动子单
          </h2>
          <p class="t-sub">
            期初、补货、申领出入库、移动接收/退回/核销及在库核销单据；详情保留业务链与每单独立 ID
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
          label="单据"
          min-width="190"
        >
          <template #default="{ row }">
            <div class="document_center__document">
              <el-button
                link
                type="primary"
                @click="openDetail(row)"
              >
                {{ row.documentNo }}
              </el-button>
              <span class="t-hint">ID {{ row.id }} · 序号 {{ row.documentSeq ?? '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="类型 / 场景"
          min-width="190"
        >
          <template #default="{ row }">
            <div class="document_center__type">
              <span>{{ typeLabel(row.documentType) }}</span>
              <span class="t-hint">{{ sceneLabel(row.businessScene) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="业务链ID"
          prop="businessChainId"
          min-width="180"
        >
          <template #default="{ row }">
            {{ row.businessChainId || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="库位 / 移动方向"
          min-width="190"
        >
          <template #default="{ row }">
            <span v-if="row.fromLocationId || row.toLocationId">{{ row.fromLocationId || '—' }} → {{ row.toLocationId || '—' }}</span>
            <span v-else>{{ row.locationName || row.locationId || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="经办人"
          min-width="110"
        >
          <template #default="{ row }">
            {{ row.operatorName || row.operatorId }}
          </template>
        </el-table-column>
        <el-table-column
          label="实际领取人"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.pickupUserName || row.pickupUserId || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="发生时间"
          min-width="155"
        >
          <template #default="{ row }">
            {{ formatTime(row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          min-width="140"
        >
          <template #default="{ row }">
            <ItemStatusPill :status="row.reviewStatus || row.transferStatus || row.processStatus || 'POSTED'" />
            <ItemStatusPill
              v-if="row.returnStatus"
              class="document_center__return_status"
              :status="row.returnStatus"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="160"
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
            <el-button
              v-if="isClaimOutbound(row)"
              v-permission="'item:claim:return'"
              link
              type="success"
              @click="openReturn(row)"
            >
              归还
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="document_center__pager">
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

    <DocumentDetailDrawer
      ref="detailDrawer"
      v-model="detailVisible"
      :document-id="selectedDocumentId"
      @return="openReturn"
    />
    <ReturnRegistrationDialog
      v-model="returnVisible"
      :source-document="returnSource"
      @success="onReturnSuccess"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import DocumentDetailDrawer from '../components/DocumentDetailDrawer.vue'
import ReturnRegistrationDialog from '../components/ReturnRegistrationDialog.vue'
import { pageDocuments } from '@/api/item/workflow'
import { formatInstant } from '@/utils/datetime'
import { businessSceneLabel, documentTypeLabel } from '@/views/item/components/claim.constants'

const emit = defineEmits(['changed'])
const documentTypes = [
  ['INBOUND', '入库'], ['OUTBOUND', '出库'], ['TRANSFER', '移动'],
  ['TRANSFER_RECEIPT', '移动实收'], ['TRANSFER_RETURN', '移动退回'],
  ['TRANSFER_SETTLEMENT', '在途核销'], ['SCRAP', '在库报废'], ['ADJUST', '在库调整']
].map(([value, label]) => ({ value, label }))
const businessScenes = [
  ['OPENING', '期初入库'], ['REPLENISHMENT', '补货入库'], ['CLAIM_ISSUE', '申领实发'],
  ['CLAIM_RETURN', '申领归还'], ['TRANSFER_SEND', '移动发出'], ['TRANSFER_RECEIVE', '移动实收'],
  ['TRANSFER_RETURN', '移动退回'], ['TRANSFER_SETTLE', '在途核销'], ['IN_STOCK_SETTLE', '在库核销']
].map(([value, label]) => ({ value, label }))
const query = reactive({ pageNum: 1, pageSize: 10, documentNo: '', businessChainId: '', documentType: '', businessScene: '', locationId: '' })
const dates = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detailDrawer = ref(null)
const selectedDocumentId = ref('')
const returnVisible = ref(false)
const returnSource = ref(null)

const formatTime = (value) => formatInstant(value) || '—'
const typeLabel = (value) => value ? documentTypeLabel(value) : '—'
const sceneLabel = (value) => value ? businessSceneLabel(value) : '—'
const isClaimOutbound = (row) => row.documentType === 'OUTBOUND'
  && row.businessScene === 'CLAIM_ISSUE' && row.processStatus !== 'PROCESSED'

async function loadData() {
  loading.value = true
  try {
    const result = await pageDocuments({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      documentNo: query.documentNo || undefined,
      businessChainId: query.businessChainId || undefined,
      documentType: query.documentType || undefined,
      businessScene: query.businessScene || undefined,
      locationId: query.locationId || undefined,
      startDate: dates.value?.[0] || undefined,
      endDate: dates.value?.[1] || undefined,
      sortBy: 'occurredAt',
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
  Object.assign(query, { pageNum: 1, documentNo: '', businessChainId: '', documentType: '', businessScene: '', locationId: '' })
  dates.value = []
  return loadData()
}

function openDetail(row) {
  selectedDocumentId.value = String(row.document?.id || row.id)
  detailVisible.value = true
}

function openReturn(source) {
  returnSource.value = source
  returnVisible.value = true
}

async function onReturnSuccess() {
  await loadData()
  if (detailVisible.value) await detailDrawer.value?.reload()
  emit('changed')
}

onMounted(loadData)
defineExpose({ reload: loadData })
</script>

<style scoped>
.document_center__filters { margin-bottom: 16px; }
.document_center__filters :deep(.el-form-item) { margin-bottom: 0; }
.document_center__select { width: 145px; }
.document_center__chain { width: 210px; }
.document_center__scene { width: 160px; }
.document_center__location { width: 200px; }
.document_center__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.document_center__head h2 { margin: 0 0 5px; }
.document_center__head p { margin: 0; }
.document_center__document, .document_center__type { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; }
.document_center__return_status { display: block; margin-top: 4px; }
.document_center__pager { display: flex; justify-content: flex-end; margin-top: 16px; overflow-x: auto; }
@media (max-width: 768px) {
  .document_center__filters :deep(.el-form-item) { margin-bottom: 10px; }
}
</style>
