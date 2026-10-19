<template>
  <div>
    <div class="toolbar">
      <LocationSelect
        v-model="query.locationId"
        placeholder="全部库位"
        all-option
        @change="pager.search"
      />
      <el-select
        v-model="query.categoryId"
        clearable
        filterable
        placeholder="全部分类"
        @change="pager.search"
      >
        <el-option
          label="全部分类"
          value=""
        />
        <el-option
          v-for="c in categories"
          :key="c.id"
          :label="c.categoryName"
          :value="c.id"
        />
      </el-select>
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
        class="w220"
        placeholder="按单据 ID 过滤"
        @change="pager.search"
      />
      <el-date-picker
        v-model="query.timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DD HH:mm:ss"
        start-placeholder="发生时间起"
        end-placeholder="发生时间止"
        class="w360"
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
        label="发生时间"
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
        label="价值变动"
        width="110"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.valueDelta) }}
        </template>
      </el-table-column>
      <el-table-column
        label="过账后均价"
        width="110"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.afterAvgPrice) }}
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
import { onMounted, reactive, ref } from 'vue'
import { listEnabledCategories, pageMovements } from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { moveDirectionMap } from '@/utils/statusMap'
import { fmtShanghai, formatQty, shanghaiToUtcIso } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import LocationSelect from '@/components/LocationSelect.vue'
import SkuSelect from '@/components/SkuSelect.vue'

const query = reactive({ locationId: null, categoryId: null, skuId: null, direction: null, stockDocumentId: '', timeRange: null })
const categories = ref([])

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

async function loadCategories() {
  try {
    categories.value = await listEnabledCategories()
  } catch {
    // 失败已由全局拦截器提示
  }
}

onMounted(() => {
  pager.load()
  loadCategories()
})

defineExpose({ refresh: pager.load })
</script>

<style scoped>
.w360 {
  width: 360px;
}
</style>
