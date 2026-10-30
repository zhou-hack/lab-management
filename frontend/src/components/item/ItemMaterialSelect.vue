<template>
  <el-select
    v-model="selectValue"
    class="item_material_select"
    :placeholder="placeholder"
    :disabled="disabled"
    :loading="loading"
    :clearable="clearable"
    filterable
    remote
    :remote-method="remoteSearch"
    @change="onChange"
  >
    <el-option
      v-for="option in options"
      :key="option.id"
      :label="optionLabel(option)"
      :value="option.id"
    >
      <div class="item_material_select__option">
        <span>{{ option.materialName }}</span>
        <span class="item_material_select__meta">
          {{ option.skuCode || MATERIAL_TYPE_LABELS[option.materialType] || '' }}
        </span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import request from '@/api/request'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  materialType: {
    type: String,
    default: '',
    validator: (value) => ['', 'CONSUMABLE', 'FIXED_ASSET'].includes(value)
  },
  disabled: { type: Boolean, default: false },
  clearable: { type: Boolean, default: true },
  placeholder: { type: String, default: '搜索并选择物料' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const MATERIAL_TYPE_LABELS = { CONSUMABLE: '耗材', FIXED_ASSET: '固定资产' }
const options = ref([])
const selectedOption = ref(null)
const loading = ref(false)
let searchTimer = null
let requestSerial = 0

const selectValue = computed({
  get: () => props.modelValue === null || props.modelValue === undefined ? '' : String(props.modelValue),
  set: (value) => emit('update:modelValue', value === null || value === undefined ? '' : String(value))
})

function optionLabel(option) {
  return [option.materialName, option.skuCode].filter(Boolean).join(' · ')
}

async function loadOptions(keyword = '') {
  const serial = ++requestSerial
  loading.value = true
  try {
    const result = await request.get('/item/materials/options', {
      params: {
        materialType: props.materialType || undefined,
        keyword: keyword || undefined
      }
    })
    if (serial !== requestSerial) return
    const normalized = (result || []).map((item) => ({ ...item, id: String(item.id) }))
    if (selectedOption.value && !normalized.some((item) => item.id === selectedOption.value.id)) {
      normalized.unshift(selectedOption.value)
    }
    options.value = normalized
  } catch {
    if (serial === requestSerial) options.value = []
  } finally {
    if (serial === requestSerial) loading.value = false
  }
}

function remoteSearch(keyword) {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => loadOptions(keyword), 300)
}

function onChange(value) {
  selectedOption.value = options.value.find((item) => item.id === String(value)) || null
  emit('change', value === null || value === undefined ? '' : String(value), selectedOption.value)
}

watch(
  () => props.materialType,
  () => {
    selectedOption.value = null
    selectValue.value = ''
    emit('change', '', null)
    loadOptions()
  }
)

onMounted(() => loadOptions())
onUnmounted(() => {
  window.clearTimeout(searchTimer)
  requestSerial += 1
})
</script>

<style scoped>
.item_material_select {
  width: 100%;
}

.item_material_select__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.item_material_select__meta {
  overflow: hidden;
  font-size: 12px;
  color: var(--g3);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
