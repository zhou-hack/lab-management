<template>
  <section class="card overdue_list">
    <div class="overdue_list__head">
      <div>
        <h3 class="overdue_list__title">
          逾期归还
        </h3>
        <p class="t-sub">
          这里只展示本人名下的逾期实发与归还记录，归还期限按上海时间展示。
        </p>
      </div>
      <el-button
        :loading="loading"
        @click="load"
      >
        刷新
      </el-button>
    </div>

    <el-alert
      title="请注意：预占不等于实发保证"
      type="warning"
      :closable="false"
      show-icon
      class="overdue_list__notice"
    >
      实发时会重新校验实际库存；逾期记录中的待还数量以实发与归还单据结算结果为准，请及时归还。
    </el-alert>

    <el-form
      :inline="true"
      class="overdue_list__filters"
      @submit.prevent
    >
      <el-form-item label="记录类型">
        <el-radio-group
          v-model="query.type"
          @change="resetAndLoad"
        >
          <el-radio-button value="UNRETURNED">
            待归还
          </el-radio-button>
          <el-radio-button value="RETURNED">
            已归还
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="期限日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          clearable
          class="overdue_list__dates"
          @change="resetAndLoad"
        />
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="records"
      class="table-scroll-x"
      :row-key="documentRowKey"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="overdue_list__materials">
            <h4 class="overdue_list__materials_title">
              物料明细
            </h4>
            <el-table
              :data="row.materials || []"
              class="table-scroll-x"
              empty-text="暂无物料明细"
            >
              <el-table-column
                label="物料"
                min-width="190"
              >
                <template #default="{ row: material }">
                  <div class="t-strong">
                    {{ material.materialName || material.materialId }}
                  </div>
                  <div class="t-hint">
                    {{ [material.specification, materialTypeText(material.materialType)].filter(Boolean).join(' · ') }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                v-if="row.type === 'UNRETURNED'"
                label="待还数量"
                min-width="120"
                align="right"
              >
                <template #default="{ row: material }">
                  <ItemQuantityText
                    :value="material.outstandingQuantity"
                    :unit="material.baseUnit"
                  />
                </template>
              </el-table-column>
              <template v-else>
                <el-table-column
                  label="归还数量"
                  min-width="120"
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
                  label="结算数量"
                  min-width="120"
                  align="right"
                >
                  <template #default="{ row: material }">
                    <ItemQuantityText
                      :value="material.settledQuantity"
                      :unit="material.baseUnit"
                    />
                  </template>
                </el-table-column>
              </template>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="类型"
        width="100"
      >
        <template #default="{ row }">
          <span
            class="pill"
            :class="row.type === 'UNRETURNED' ? 'pill--danger' : 'pill--ok'"
          >
            {{ row.type === 'UNRETURNED' ? '待归还' : '已归还' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        label="申领单"
        min-width="150"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="emit('open-claim', String(row.claimId))"
          >
            {{ row.claimDocumentNo || row.claimId }}
          </el-button>
          <div class="t-hint">
            ID：{{ row.claimId }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="实发单 / 归还单"
        min-width="180"
      >
        <template #default="{ row }">
          <div>{{ row.sourceDocumentNo || row.sourceDocumentId }}</div>
          <div
            v-if="row.type === 'RETURNED'"
            class="t-hint"
          >
            {{ row.returnDocumentNo || row.returnDocumentId }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="库位"
        min-width="150"
      >
        <template #default="{ row }">
          <div>{{ row.locationName || row.locationId || '—' }}</div>
          <div class="t-hint">
            {{ row.locationCode || '' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="待还数量"
        min-width="120"
        align="right"
      >
        <template #default="{ row }">
          <ItemQuantityText
            v-if="row.type === 'UNRETURNED'"
            :value="row.outstandingQuantity"
            class="overdue_list__outstanding"
          />
          <span
            v-else
            class="t-hint"
          >—</span>
        </template>
      </el-table-column>
      <el-table-column
        label="归还期限"
        min-width="170"
      >
        <template #default="{ row }">
          {{ formatInstant(row.returnDeadline) || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="逾期时长"
        min-width="130"
      >
        <template #default="{ row }">
          <span class="overdue_list__duration">{{ formatDuration(row.overdueSeconds) }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="实发 / 归还时间"
        min-width="180"
      >
        <template #default="{ row }">
          <div>{{ formatInstant(row.sourcePostedAt) || '—' }}</div>
          <div
            v-if="row.type === 'RETURNED'"
            class="t-hint"
          >
            {{ formatInstant(row.postedAt) || '—' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="逾期原因"
        min-width="160"
      >
        <template #default="{ row }">
          <span :class="{ 't-hint': !row.overdueReason }">{{ row.overdueReason || '—' }}</span>
        </template>
      </el-table-column>
      <template #empty>
        <span class="t-hint">{{ query.type === 'UNRETURNED' ? '暂无待归还逾期记录' : '暂无已归还逾期记录' }}</span>
      </template>
    </el-table>

    <div class="overdue_list__pager">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="changePageSize"
        @current-change="load"
      />
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import { pageMyOverdues } from '@/api/item/claim'
import { formatInstant } from '@/utils/datetime'

const emit = defineEmits(['open-claim'])
const records = ref([])
const total = ref(0)
const loading = ref(false)
const dateRange = ref([])
const query = reactive({
  type: 'UNRETURNED',
  pageNum: 1,
  pageSize: 10
})

function materialTypeText(type) {
  return type === 'FIXED_ASSET' ? '固定资产' : type === 'CONSUMABLE' ? '耗材' : ''
}

function documentRowKey(row) {
  return String(row.type === 'RETURNED' ? row.returnDocumentId : row.sourceDocumentId)
}

function formatDuration(value) {
  const seconds = Math.max(0, Number(value) || 0)
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days) return `${days} 天 ${hours} 小时`
  if (hours) return `${hours} 小时 ${minutes} 分钟`
  return `${minutes} 分钟`
}

async function load() {
  loading.value = true
  try {
    const data = await pageMyOverdues({
      type: query.type,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
    })
    records.value = data?.records || []
    total.value = Number(data?.total) || 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  query.pageNum = 1
  load()
}

function changePageSize() {
  query.pageNum = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.overdue_list {
  padding: 20px;
}

.overdue_list__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.overdue_list__title {
  margin: 0 0 5px;
  font-size: 16px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.overdue_list__head p {
  margin: 0;
}

.overdue_list__notice {
  margin-bottom: 16px;
}

.overdue_list__filters {
  display: flex;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.overdue_list__dates {
  width: 270px;
}

.overdue_list__materials {
  padding: 8px 22px 16px 54px;
  background: var(--surface-sunken);
}

.overdue_list__materials_title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: var(--fw-semibold);
  color: var(--g2);
}

.overdue_list__outstanding :deep(.item_quantity_text__value),
.overdue_list__duration {
  color: var(--danger);
  font-weight: var(--fw-semibold);
}

.overdue_list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .overdue_list {
    padding: 14px;
  }

  .overdue_list__head {
    flex-direction: column;
  }

  .overdue_list__dates {
    width: 100%;
  }

  .overdue_list__materials {
    padding: 8px;
  }
}
</style>
