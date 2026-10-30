<template>
  <div class="overdue_panel">
    <div class="card">
      <div class="overdue_panel__head">
        <div>
          <h2 class="t-title">
            申领逾期记录
          </h2>
          <p class="t-sub">
            未还与已还逾期分栏保留；归还完成不会掩盖历史逾期时长和原因
          </p>
        </div>
        <el-button
          :loading="loading"
          @click="loadData"
        >
          刷新
        </el-button>
      </div>
      <el-tabs
        v-model="query.type"
        @tab-change="onTabChange"
      >
        <el-tab-pane
          label="当前未还"
          name="UNRETURNED"
        />
        <el-tab-pane
          label="历史已还"
          name="RETURNED"
        />
      </el-tabs>
      <el-form
        :inline="true"
        class="overdue_panel__filters"
        @submit.prevent
      >
        <el-form-item label="申领ID">
          <el-input
            v-model="query.claimId"
            clearable
            placeholder="申领ID"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="出库单ID">
          <el-input
            v-model="query.sourceDocumentId"
            clearable
            placeholder="来源出库单ID"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="申请人ID">
          <el-input
            v-model="query.applicantId"
            clearable
            placeholder="申请人ID"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="库位">
          <ItemLocationSelect
            v-model="query.locationId"
            class="overdue_panel__location"
          />
        </el-form-item>
        <el-form-item label="日期">
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

      <el-table
        v-loading="loading"
        :data="records"
        row-key="rowKey"
        class="table-scroll-x"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="overdue_panel__materials">
              <el-table
                :data="row.materials || []"
                size="small"
              >
                <el-table-column
                  label="物料"
                  min-width="190"
                >
                  <template #default="{ row: material }">
                    <strong>{{ material.materialName }}</strong>
                    <span class="t-hint overdue_panel__block">{{ material.specification || material.materialId }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  label="类型"
                  width="110"
                >
                  <template #default="{ row: material }">
                    {{ material.materialType === 'FIXED_ASSET' ? '固定资产' : '耗材' }}
                  </template>
                </el-table-column>
                <el-table-column
                  label="待还"
                  width="120"
                  align="right"
                >
                  <template #default="{ row: material }">
                    <ItemQuantityText
                      :value="material.outstandingQuantity"
                      :unit="material.baseUnit"
                    />
                  </template>
                </el-table-column>
                <el-table-column
                  label="已归还"
                  width="120"
                  align="right"
                >
                  <template #default="{ row: material }">
                    <ItemQuantityText
                      :value="material.returnedQuantity"
                      :unit="material.baseUnit"
                    />
                  </template>
                </el-table-column>
                <el-table-column
                  label="已核销"
                  width="120"
                  align="right"
                >
                  <template #default="{ row: material }">
                    <ItemQuantityText
                      :value="material.settledQuantity"
                      :unit="material.baseUnit"
                    />
                  </template>
                </el-table-column>
              </el-table>
              <p class="t-hint overdue_panel__ids">
                申领ID {{ row.claimId }} · 来源库存单ID {{ row.sourceDocumentId }}<template v-if="row.returnDocumentId">
                  · 归还单ID {{ row.returnDocumentId }}
                </template>
              </p>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="申领 / 出库单"
          min-width="200"
        >
          <template #default="{ row }">
            <div class="overdue_panel__document">
              <strong>{{ row.claimDocumentNo || row.claimId }}</strong>
              <span class="t-hint">出库 {{ row.sourceDocumentNo || row.sourceDocumentId }}</span>
              <span
                v-if="row.returnDocumentNo"
                class="t-hint"
              >归还 {{ row.returnDocumentNo }}</span>
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
          label="物料"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ (row.materials || []).map((item) => item.materialName).join('、') || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="待还总量"
          width="120"
          align="right"
        >
          <template #default="{ row }">
            <ItemQuantityText :value="row.outstandingQuantity" />
          </template>
        </el-table-column>
        <el-table-column
          label="库位"
          min-width="160"
        >
          <template #default="{ row }">
            {{ row.locationName || row.locationId }}<span class="t-hint overdue_panel__block">{{ row.locationCode }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="归还期限"
          min-width="155"
        >
          <template #default="{ row }">
            {{ formatTime(row.returnDeadline) }}
          </template>
        </el-table-column>
        <el-table-column
          label="逾期时长"
          width="130"
        >
          <template #default="{ row }">
            <span class="overdue_panel__duration">{{ formatDuration(row.overdueSeconds) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="类型"
          width="105"
        >
          <template #default="{ row }">
            <ItemStatusPill
              :status="row.type"
              :label="row.type === 'RETURNED' ? '历史已还' : '当前未还'"
            />
          </template>
        </el-table-column>
        <el-table-column
          prop="overdueReason"
          label="逾期原因"
          min-width="200"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.overdueReason || '—' }}
          </template>
        </el-table-column>
      </el-table>
      <div class="overdue_panel__pager">
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import { pageOverdues } from '@/api/item/workflow'
import { formatInstant } from '@/utils/datetime'

const query = reactive({
  pageNum: 1, pageSize: 20, type: 'UNRETURNED', claimId: '', sourceDocumentId: '', applicantId: '', locationId: ''
})
const dates = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(false)
const formatTime = (value) => formatInstant(value) || '—'

function formatDuration(seconds) {
  const value = Math.max(0, Number(seconds || 0))
  const days = Math.floor(value / 86400)
  const hours = Math.floor((value % 86400) / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  if (days) return `${days}天${hours}小时`
  if (hours) return `${hours}小时${minutes}分`
  if (minutes) return `${minutes}分钟`
  return `${Math.floor(value)}秒`
}

async function loadData() {
  loading.value = true
  try {
    const result = await pageOverdues({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      type: query.type,
      claimId: query.claimId || undefined,
      sourceDocumentId: query.sourceDocumentId || undefined,
      applicantId: query.applicantId || undefined,
      locationId: query.locationId || undefined,
      startDate: dates.value?.[0] || undefined,
      endDate: dates.value?.[1] || undefined,
      sortBy: 'returnDeadline',
      sortOrder: 'desc'
    })
    records.value = (result?.records || []).map((row, index) => ({
      ...row,
      rowKey: `${row.type}-${row.sourceDocumentId}-${row.returnDocumentId || index}`
    }))
    total.value = Number(result?.total || 0)
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  return loadData()
}

function onTabChange() {
  return search()
}

function reset() {
  Object.assign(query, { pageNum: 1, claimId: '', sourceDocumentId: '', applicantId: '', locationId: '' })
  dates.value = []
  return loadData()
}

onMounted(loadData)
defineExpose({ reload: loadData })
</script>

<style scoped>
.overdue_panel__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.overdue_panel__head h2 { margin: 0 0 5px; }
.overdue_panel__head p { margin: 0; }
.overdue_panel__filters { padding-bottom: 4px; }
.overdue_panel__location { width: 200px; }
.overdue_panel__document { display: flex; flex-direction: column; gap: 3px; }
.overdue_panel__duration { color: var(--danger); font-weight: var(--fw-semibold); }
.overdue_panel__block { display: block; }
.overdue_panel__materials { padding: 8px 18px; }
.overdue_panel__ids { margin: 10px 0 0; overflow-wrap: anywhere; }
.overdue_panel__pager { display: flex; justify-content: flex-end; margin-top: 16px; overflow-x: auto; }
</style>
