<template>
  <div class="material_catalog">
    <div class="material_catalog__section_head">
      <div>
        <h4 class="material_catalog__section_title">
          {{ mode === 'plan' ? '物料目录' : '库存目录' }}
        </h4>
        <p class="t-hint">
          {{ mode === 'plan'
            ? '从耗材或固定资产目录选择物料并填写默认数量；同一物料重复添加会自动合并数量。'
            : '按当前预约日期与节次查询预计可约量；同一物料重复添加会自动合并数量。' }}
        </p>
      </div>
    </div>
    <el-tabs
      v-model="catalogType"
      class="material_catalog__tabs"
      @tab-change="search"
    >
      <el-tab-pane
        label="耗材"
        name="CONSUMABLE"
      />
      <el-tab-pane
        label="固定资产"
        name="FIXED_ASSET"
      />
    </el-tabs>
    <div class="material_catalog__filters">
      <el-input
        v-model="catalogKeyword"
        placeholder="按物料名称搜索"
        clearable
        @keyup.enter="search"
        @clear="search"
      />
      <el-button
        :loading="catalogLoading"
        @click="search"
      >
        搜索
      </el-button>
    </div>
    <el-alert
      v-if="catalogError"
      type="error"
      :closable="false"
      show-icon
      :title="catalogError"
      class="material_catalog__error"
    />
    <el-table
      v-loading="catalogLoading"
      :data="catalogRecords"
      row-key="materialId"
      max-height="320"
      :empty-text="mode === 'plan' ? '当前条件下暂无物料' : '当前条件下暂无可申领物料'"
      class="table-scroll-x material_catalog__table"
    >
      <el-table-column
        prop="materialName"
        label="名称"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        label="规格"
        min-width="120"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.specification || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="保有"
        min-width="85"
        align="right"
      >
        <template #default="{ row }">
          <ItemQuantityText :value="row.ownedQuantity" />
        </template>
      </el-table-column>
      <el-table-column
        v-if="mode === 'claim'"
        label="预计可约"
        min-width="100"
        align="right"
      >
        <template #default="{ row }">
          <ItemQuantityText :value="row.availableQuantity" />
        </template>
      </el-table-column>
      <el-table-column
        label="单位"
        width="65"
      >
        <template #default="{ row }">
          {{ row.baseUnit || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        :label="quantityLabel"
        width="150"
      >
        <template #default="{ row }">
          <el-input-number
            v-model="catalogDrafts[row.materialId]"
            :min="canAdd(row) ? 1 : 0"
            :precision="0"
            :step="1"
            :controls="false"
            :disabled="!canAdd(row)"
            placeholder="数量"
            class="material_catalog__number"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="75"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="!canAdd(row)"
            @click="addToCart(row)"
          >
            添加
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div
      v-if="catalogTotal > 0"
      class="material_catalog__pagination"
    >
      <el-pagination
        v-model:current-page="catalogPage"
        :page-size="catalogPageSize"
        :total="catalogTotal"
        layout="total, prev, pager, next"
        background
        small
        @current-change="reload"
      />
    </div>

    <div class="material_catalog__cart_head">
      <div>
        <h4 class="material_catalog__section_title">
          购物车
        </h4>
        <p class="t-hint">
          可继续修改或删除物料；所有物料数量必须为整数，保存时不能为空。
        </p>
      </div>
      <span class="t-hint">已选 {{ modelValue.length }} 项</span>
    </div>
    <div class="material_catalog__cart_lines">
      <MaterialLinesEditor
        :model-value="modelValue"
        :quantity-field="cartQuantityField"
        :quantity-label="quantityLabel"
        allow-empty
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import MaterialLinesEditor from './MaterialLinesEditor.vue'
import { addMaterialLine } from './materialCart.rules'

const props = defineProps({
  mode: {
    type: String,
    default: 'claim',
    validator: (value) => ['claim', 'plan'].includes(value)
  },
  modelValue: { type: Array, default: () => [] },
  loadCatalog: { type: Function, required: true },
  active: { type: Boolean, default: true },
  refreshKey: { type: [String, Number], default: '' },
  quantityField: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const catalogType = ref('CONSUMABLE')
const catalogKeyword = ref('')
const catalogPage = ref(1)
const catalogPageSize = 10
const catalogRecords = ref([])
const catalogTotal = ref(0)
const catalogDrafts = ref({})
const catalogLoading = ref(false)
const catalogError = ref('')
let catalogSerial = 0

const cartQuantityField = computed(() => props.quantityField
  || (props.mode === 'plan' ? 'defaultQuantity' : 'requestedQuantity'))
const quantityLabel = computed(() => props.mode === 'plan' ? '默认数量' : '申请数量')

function resetCatalog() {
  catalogType.value = 'CONSUMABLE'
  catalogKeyword.value = ''
  catalogPage.value = 1
  catalogRecords.value = []
  catalogTotal.value = 0
  catalogDrafts.value = {}
  catalogLoading.value = false
  catalogError.value = ''
}

async function reload() {
  if (!props.active) return
  const serial = ++catalogSerial
  catalogLoading.value = true
  catalogError.value = ''
  try {
    const result = await props.loadCatalog({
      materialType: catalogType.value,
      materialName: catalogKeyword.value.trim() || undefined,
      pageNum: catalogPage.value,
      pageSize: catalogPageSize
    })
    if (serial !== catalogSerial || !props.active) return
    const records = (result?.records || []).map((record) => ({
      ...record,
      materialId: String(record.materialId)
    }))
    catalogRecords.value = records
    catalogTotal.value = Number(result?.total) || 0
    catalogDrafts.value = Object.fromEntries(records.map((record) => [record.materialId, null]))
  } catch (error) {
    if (serial !== catalogSerial || !props.active) return
    catalogRecords.value = []
    catalogTotal.value = 0
    catalogDrafts.value = {}
    catalogError.value = error?.response?.data?.msg || error?.message || '物料目录加载失败，请重试'
  } finally {
    if (serial === catalogSerial) catalogLoading.value = false
  }
}

function search() {
  catalogPage.value = 1
  reload()
}

function canAdd(material) {
  return props.mode === 'plan' || Number(material.availableQuantity) > 0
}

function addToCart(material) {
  const result = addMaterialLine(
    props.modelValue,
    material,
    catalogDrafts.value[material.materialId],
    { mode: props.mode, quantityField: cartQuantityField.value }
  )
  if (result.error) {
    ElMessage.warning(result.error)
    return
  }
  emit('update:modelValue', result.lines)
  emit('change', result.lines)
  catalogDrafts.value[material.materialId] = null
}

watch(
  () => props.active,
  (active) => {
    catalogSerial += 1
    resetCatalog()
    if (active) reload()
  },
  { immediate: true }
)

watch(
  () => props.refreshKey,
  (value, previous) => {
    if (props.active && value !== previous) search()
  }
)

defineExpose({ reload, search })
</script>

<style scoped>
.material_catalog {
  width: 100%;
}

.material_catalog__section_title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.material_catalog__tabs {
  margin-bottom: 4px;
}

.material_catalog__filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.material_catalog__filters .el-input {
  max-width: 340px;
}

.material_catalog__error {
  margin-bottom: 12px;
}

.material_catalog__number {
  width: 125px;
}

.material_catalog__pagination {
  display: flex;
  justify-content: flex-end;
  margin: -8px 0 20px;
}

.material_catalog__cart_head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.material_catalog__cart_lines {
  max-height: 280px;
  padding-right: 4px;
  overflow-y: auto;
}

.material_catalog__table {
  margin-bottom: 20px;
}
</style>
