<template>
  <div class="settlement_detail">
    <div class="card settlement_detail__filters">
      <el-form
        :inline="true"
        @submit.prevent
      >
        <el-form-item label="单据号">
          <el-input
            v-model="query.documentNo"
            clearable
            placeholder="精确 / 模糊查询"
            class="settlement_detail__document"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="物料">
          <ItemMaterialSelect
            v-model="query.materialId"
            class="settlement_detail__material"
          />
        </el-form-item>
        <el-form-item label="核销类型">
          <el-select
            v-model="query.settlementType"
            clearable
            placeholder="全部类型"
            class="settlement_detail__type"
          >
            <el-option
              v-for="item in settlementTypes"
              :key="item"
              :label="settlementTypeLabel(item)"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库位">
          <ItemLocationSelect
            v-model="query.locationId"
            class="settlement_detail__location"
            placeholder="全部库位"
          />
        </el-form-item>
        <el-form-item label="复核状态">
          <el-select
            v-model="query.reviewStatus"
            clearable
            placeholder="全部状态"
            class="settlement_detail__review"
          >
            <el-option
              label="待复核"
              value="PENDING_REVIEW"
            />
            <el-option
              label="已批准"
              value="APPROVED"
            />
            <el-option
              label="已驳回"
              value="REJECTED"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发生日期">
          <el-date-picker
            v-model="dates"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            class="settlement_detail__dates"
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

    <div class="card settlement_detail__content">
      <div class="settlement_detail__head">
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
          label="库存单据"
          min-width="195"
          fixed="left"
        >
          <template #default="{ row }">
            <div class="settlement_detail__document_cell">
              <el-button
                link
                type="primary"
                @click="openDetail(row)"
              >
                {{ row.documentNo || row.materialDocumentId }}
              </el-button>
              <span class="t-hint">ID {{ row.materialDocumentId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="发生时间 / 场景"
          min-width="185"
        >
          <template #default="{ row }">
            <div>{{ formatTime(row.occurredAt) }}</div>
            <div class="t-hint">
              {{ businessSceneLabel(row.businessScene) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="物料 / 资产"
          min-width="190"
        >
          <template #default="{ row }">
            <div class="settlement_detail__strong">
              {{ row.materialName || row.materialId }}
            </div>
            <div class="t-hint">
              {{ row.specification || '—' }}
            </div>
            <div
              v-if="row.assetCode || row.fixedAssetId"
              class="t-hint"
            >
              {{ row.assetCode || row.fixedAssetId }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="核销类型"
          min-width="105"
        >
          <template #default="{ row }">
            {{ settlementTypeLabel(row.settlementType) }}
          </template>
        </el-table-column>
        <el-table-column
          label="数量"
          min-width="100"
          align="right"
        >
          <template #default="{ row }">
            <ItemQuantityText
              :value="row.quantity"
              :unit="row.baseUnit"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="目标状态"
          min-width="105"
        >
          <template #default="{ row }">
            {{ row.targetAssetStatus ? assetStatusLabel(row.targetAssetStatus) : '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="库位"
          min-width="175"
        >
          <template #default="{ row }">
            <div>{{ row.locationName || row.locationId || '—' }}</div>
            <div class="t-hint">
              {{ row.locationCode || '' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="经办人"
          min-width="115"
        >
          <template #default="{ row }">
            {{ row.operatorName || row.operatorId || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="复核 / 过账"
          min-width="195"
        >
          <template #default="{ row }">
            <div class="settlement_detail__review_status">
              <ItemStatusPill
                v-if="row.reviewStatus"
                :status="row.reviewStatus"
              />
              <ItemStatusPill
                v-else-if="row.postedAt"
                status="POSTED"
              />
              <span
                v-else
                class="t-hint"
              >—</span>
              <span class="t-hint">{{ row.postedAt ? '已过账' : '未过账' }}</span>
            </div>
            <div
              v-if="row.reviewedByName || row.reviewedBy || row.reviewedAt"
              class="t-hint"
            >
              {{ row.reviewedByName || row.reviewedBy || '—' }}
              <template v-if="row.reviewedAt">
                · {{ formatTime(row.reviewedAt) }}
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="原因 / 来源行"
          min-width="230"
        >
          <template #default="{ row }">
            <div class="settlement_detail__reason">
              {{ row.reason || '—' }}
            </div>
            <div class="t-hint">
              来源行 ID：{{ row.sourceMaterialDocumentLineId || '—' }}
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            description="暂无符合条件的核销明细"
            :image-size="64"
          />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="settlement_detail__pager"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        background
        @size-change="onSizeChange"
        @current-change="loadData"
      />
    </div>

    <DocumentDetailDrawer
      v-model="detailVisible"
      :document-id="selectedDocumentId"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemMaterialSelect from '@/components/item/ItemMaterialSelect.vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import DocumentDetailDrawer from '../components/DocumentDetailDrawer.vue'
import { pageSettlements } from '@/api/item/workflow'
import { formatInstant } from '@/utils/datetime'
import {
  assetStatusLabel,
  businessSceneLabel,
  settlementTypeLabel
} from '@/views/item/components/claim.constants'

const settlementTypes = ['CONSUMED', 'LOST', 'DAMAGED', 'SCRAP', 'ADJUST']
const query = reactive({
  documentNo: '',
  materialId: '',
  settlementType: '',
  locationId: '',
  reviewStatus: '',
  pageNum: 1,
  pageSize: 20,
  sortBy: 'occurredAt',
  sortOrder: 'desc'
})
const dates = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const selectedDocumentId = ref('')

const formatTime = (value) => formatInstant(value) || '—'

async function loadData() {
  loading.value = true
  try {
    const result = await pageSettlements({
      ...query,
      documentNo: query.documentNo.trim() || undefined,
      materialId: query.materialId || undefined,
      settlementType: query.settlementType || undefined,
      locationId: query.locationId || undefined,
      reviewStatus: query.reviewStatus || undefined,
      startDate: dates.value?.[0] || undefined,
      endDate: dates.value?.[1] || undefined
    })
    records.value = result?.records || []
    total.value = Number(result?.total || 0)
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadData()
}

function reset() {
  Object.assign(query, {
    documentNo: '',
    materialId: '',
    settlementType: '',
    locationId: '',
    reviewStatus: '',
    pageNum: 1,
    pageSize: 20,
    sortBy: 'occurredAt',
    sortOrder: 'desc'
  })
  dates.value = []
  loadData()
}

function onSizeChange() {
  query.pageNum = 1
  loadData()
}

function openDetail(row) {
  selectedDocumentId.value = row.materialDocumentId
  detailVisible.value = true
}

onMounted(loadData)
</script>

<style scoped>
.settlement_detail__filters {
  margin-bottom: 16px;
  padding: 18px 18px 4px;
}

.settlement_detail__content {
  padding: 18px;
}

.settlement_detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 16px;
  margin-bottom: 14px;
}

.settlement_detail__document_cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.settlement_detail__strong {
  font-weight: var(--fw-medium);
}

.settlement_detail__review_status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.settlement_detail__reason {
  overflow-wrap: anywhere;
}

.settlement_detail__pager {
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

.settlement_detail__document {
  width: 170px;
}

.settlement_detail__material {
  width: 190px;
}

.settlement_detail__type {
  width: 135px;
}

.settlement_detail__location {
  width: 185px;
}

.settlement_detail__review {
  width: 135px;
}

.settlement_detail__dates {
  width: 250px;
}

@media (max-width: 768px) {
  .settlement_detail__filters,
  .settlement_detail__content {
    padding: 14px;
  }
}
</style>
