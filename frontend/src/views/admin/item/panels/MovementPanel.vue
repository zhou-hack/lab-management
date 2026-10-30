<template>
  <div class="movement_panel card">
    <div class="movement_panel__toolbar">
      <el-input
        v-model="query.materialName"
        placeholder="物料名称"
        clearable
        class="movement_panel__input"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select
        v-model="query.materialType"
        placeholder="全部物料类型"
        clearable
        class="movement_panel__type"
        @change="search"
      >
        <el-option
          label="耗材"
          value="CONSUMABLE"
        />
        <el-option
          label="固定资产"
          value="FIXED_ASSET"
        />
      </el-select>
      <item-location-select
        v-model="query.locationId"
        class="movement_panel__location"
        placeholder="全部库位"
        @change="search"
      />
      <el-select
        v-model="query.direction"
        placeholder="全部方向"
        clearable
        class="movement_panel__direction"
        @change="search"
      >
        <el-option
          label="入库 IN"
          value="IN"
        />
        <el-option
          label="出库 OUT"
          value="OUT"
        />
      </el-select>
      <el-input
        v-model="query.movementType"
        placeholder="业务类型，如 OPENING"
        clearable
        class="movement_panel__type_input"
        @keyup.enter="search"
        @clear="search"
      />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        class="movement_panel__dates"
        @change="search"
      />
      <el-button
        type="primary"
        @click="search"
      >
        查询
      </el-button>
      <el-button @click="reset">
        重置
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="records"
      row-key="id"
    >
      <el-table-column
        prop="materialName"
        label="物料"
        min-width="130"
        fixed="left"
      />
      <el-table-column
        label="发生时间"
        min-width="155"
      >
        <template #default="{ row }">
          {{ formatInstant(row.occurredAt, { withSeconds: true }) || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="资产编码"
        min-width="125"
      >
        <template #default="{ row }">
          {{ row.assetCode || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="库位"
        min-width="130"
      >
        <template #default="{ row }">
          <div>{{ row.locationName || '—' }}</div>
          <div class="t-hint">
            {{ row.locationCode || '' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="业务类型"
        min-width="145"
      >
        <template #default="{ row }">
          {{ MOVEMENT_TYPE_LABELS[row.movementType] || row.movementType }}
          <div class="t-hint">
            {{ row.movementType }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="方向"
        width="85"
      >
        <template #default="{ row }">
          <span
            class="pill"
            :class="row.direction === 'IN' ? 'pill--ok' : 'pill--warn'"
          >
            {{ row.direction === 'IN' ? '入库' : '出库' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        label="数量"
        min-width="100"
        align="right"
      >
        <template #default="{ row }">
          <item-quantity-text
            :value="row.quantity"
            :unit="row.baseUnit"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="保有：前 → 后"
        min-width="180"
        align="right"
      >
        <template #default="{ row }">
          <div class="movement_panel__balance">
            <item-quantity-text :value="row.beforeOwnedQuantity" />
            <span>→</span>
            <item-quantity-text
              :value="row.afterOwnedQuantity"
              :unit="row.baseUnit"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="可用：前 → 后"
        min-width="180"
        align="right"
      >
        <template #default="{ row }">
          <div class="movement_panel__balance">
            <item-quantity-text :value="row.beforeAvailableQuantity" />
            <span>→</span>
            <item-quantity-text
              :value="row.afterAvailableQuantity"
              :unit="row.baseUnit"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="依据单据"
        min-width="170"
      >
        <template #default="{ row }">
          <div>{{ row.documentNo || '—' }}</div>
          <div
            v-if="row.materialDocumentId"
            class="t-hint"
          >
            ID {{ row.materialDocumentId }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="操作人"
        min-width="100"
      >
        <template #default="{ row }">
          {{ row.operatorName || '—' }}
        </template>
      </el-table-column>
      <template #empty>
        <el-empty
          description="暂无符合条件的库存流水"
          :image-size="64"
        />
      </template>
    </el-table>
    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      class="movement_panel__pager"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      background
      @size-change="onSizeChange"
      @current-change="loadData"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import { getMovements } from '@/api/item/stock'
import { formatInstant } from '@/utils/datetime'

const MOVEMENT_TYPE_LABELS = {
  OPENING: '期初入库',
  REPLENISHMENT: '补货入库',
  CLAIM_ISSUE: '申领实发',
  CLAIM_RETURN: '申领归还',
  TRANSFER_SEND: '移动发出',
  TRANSFER_RECEIVE: '移动接收',
  TRANSFER_RETURN: '移动退回',
  SCRAP: '报废',
  ADJUST: '库存调整'
}

const query = reactive({
  materialName: '',
  materialType: '',
  locationId: '',
  direction: '',
  movementType: '',
  pageNum: 1,
  pageSize: 10
})
const dateRange = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const result = await getMovements({
      ...query,
      materialName: query.materialName.trim() || undefined,
      materialType: query.materialType || undefined,
      locationId: query.locationId || undefined,
      direction: query.direction || undefined,
      movementType: query.movementType.trim() || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
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
    materialName: '',
    materialType: '',
    locationId: '',
    direction: '',
    movementType: '',
    pageNum: 1,
    pageSize: 10
  })
  dateRange.value = []
  loadData()
}

function onSizeChange() {
  query.pageNum = 1
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.movement_panel {
  padding: 18px;
}

.movement_panel__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  overflow-x: auto;
}

.movement_panel__toolbar > * {
  flex-shrink: 0;
}

.movement_panel__input,
.movement_panel__location {
  width: 180px;
}

.movement_panel__type {
  width: 150px;
}

.movement_panel__direction {
  width: 125px;
}

.movement_panel__type_input {
  width: 190px;
}

.movement_panel__dates {
  width: 260px;
}

.movement_panel__balance {
  display: inline-flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 6px;
  white-space: nowrap;
}

.movement_panel__balance > span {
  color: var(--g4);
}

.movement_panel__pager {
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

@media (max-width: 768px) {
  .movement_panel {
    padding: 14px;
  }
}
</style>
