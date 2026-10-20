<template>
  <div>
    <div class="toolbar">
      <LocationSelect
        v-model="query.locationId"
        placeholder="全部库位"
        all-option
        @change="pager.search"
      />
      <CategorySelect
        v-model="query.categoryId"
        all-option
        @change="pager.search"
      />
      <SkuSelect
        v-model="query.skuId"
        all-option
        @change="pager.search"
      />
      <el-select
        v-model="query.direction"
        clearable
        placeholder="全部方向"
        @change="pager.search"
      >
        <el-option
          label="全部方向"
          value=""
        />
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
        v-model="query.stockDocumentId"
        clearable
        class="w150"
        placeholder="按单据 ID 过滤"
        @change="pager.search"
      />
      <el-date-picker
        v-model="query.timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DD HH:mm:ss"
        start-placeholder="出入库时间起"
        end-placeholder="出入库时间止"
        class="w320"
        @change="pager.search"
      />
      <el-button @click="pager.search">
        查询
      </el-button>
    </div>

    <el-table
      v-loading="pager.loading"
      :data="pager.rows"
      border
    >
      <el-table-column
        label="出入库时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.occurredAt) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="locationName"
        label="库位"
        min-width="170"
        show-overflow-tooltip
      />
      <el-table-column
        label="物料"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <div>{{ row.skuName }}</div>
          <div class="muted small">
            {{ row.skuCode }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="方向"
        width="80"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.direction"
            :map="moveDirectionMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="净变动"
        width="100"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.qtyDelta) }}
        </template>
      </el-table-column>
      <el-table-column
        label="变更前"
        width="90"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.beforeQty) }}
        </template>
      </el-table-column>
      <el-table-column
        label="变更后"
        width="90"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.afterQty) }}
        </template>
      </el-table-column>
      <el-table-column
        label="入库人"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          {{ row.direction === 'IN' ? displayName(row.operatorName, row.operatorId) : '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="出库人"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          {{ row.direction === 'OUT' ? displayName(row.operatorName, row.operatorId) : '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="保管人"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          {{ displayName(row.custodianName, row.custodianId) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="stockDocumentId"
        label="单据 ID"
        width="180"
        show-overflow-tooltip
      />
    </el-table>

    <PagerBar :list="pager" />
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import { pageMovements } from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { moveDirectionMap } from '@/utils/statusMap'
import { fmtShanghai, formatQty, shanghaiToUtcIso } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import LocationSelect from '@/components/LocationSelect.vue'
import SkuSelect from '@/components/SkuSelect.vue'
import CategorySelect from '@/components/CategorySelect.vue'

const query = reactive({ locationId: null, categoryId: null, skuId: null, direction: null, stockDocumentId: '', timeRange: null })

const pager = usePagedList((params) => {
  const range = query.timeRange || []
  return pageMovements({
    ...params,
    locationId: query.locationId || undefined,
    categoryId: query.categoryId || undefined,
    skuId: query.skuId || undefined,
    direction: query.direction || undefined,
    stockDocumentId: query.stockDocumentId || undefined,
    occurredFrom: range[0] ? shanghaiToUtcIso(range[0]) : undefined,
    occurredTo: range[1] ? shanghaiToUtcIso(range[1]) : undefined
  })
})

/** 姓名/ID 均缺失（经办人未登记）时显示占位 */
function displayName(name, id) {
  if (name) return name
  return id || '—'
}

onMounted(() => {
  pager.load()
})

defineExpose({ refresh: pager.load })
</script>

<style scoped>
/* 单行工具栏压缩：默认状态总宽约 1318px，三个搜索下拉选中长文本撑满
   （各 220px）时约 1378px，均不超过工作台内容区宽（1392px），保证不换行不滚动 */
.toolbar :deep(.el-input.w150) {
  width: 150px;
  min-width: 150px;
}

/* EP 对 datetimerange 有主题默认宽（约 400px），普通单类选择器不稳；
   用 .toolbar + 组件类 + 自定义类三重抬高特异性压到 320px */
.toolbar :deep(.el-date-editor--datetimerange.w320) {
  width: 320px;
}
</style>
