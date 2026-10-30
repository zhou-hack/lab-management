<template>
  <el-table
    v-loading="loading"
    :data="items"
    class="table-scroll-x stock_shortage_table"
    empty-text="暂无库存缺口"
  >
    <el-table-column
      prop="materialName"
      label="物料"
      min-width="180"
      show-overflow-tooltip
    />
    <el-table-column
      label="库位"
      min-width="180"
    >
      <template #default="{ row }">
        <div class="stock_shortage_table__location">
          <span>{{ row.locationName || '—' }}</span>
          <span
            v-if="row.locationCode"
            class="t-hint"
          >{{ row.locationCode }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column
      label="申请"
      min-width="110"
      align="right"
    >
      <template #default="{ row }">
        <ItemQuantityText :value="row.requestedQuantity" />
      </template>
    </el-table-column>
    <el-table-column
      label="可约"
      min-width="110"
      align="right"
    >
      <template #default="{ row }">
        <ItemQuantityText :value="row.availableQuantity" />
      </template>
    </el-table-column>
    <el-table-column
      label="缺口"
      min-width="110"
      align="right"
    >
      <template #default="{ row }">
        <ItemQuantityText
          :value="row.shortageQuantity"
          class="stock_shortage_table__shortage"
        />
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import ItemQuantityText from './ItemQuantityText.vue'

defineProps({
  items: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})
</script>

<style scoped>
.stock_shortage_table__location {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stock_shortage_table__shortage :deep(.item_quantity_text__value) {
  font-weight: var(--fw-semibold);
  color: var(--danger);
}
</style>
