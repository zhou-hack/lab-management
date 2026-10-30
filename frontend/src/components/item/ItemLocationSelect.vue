<template>
  <el-select
    v-model="selectValue"
    class="item_location_select"
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
      :disabled="option.enabled === 0"
    >
      <div class="item_location_select__option">
        <span>{{ option.locationName }}</span>
        <span
          v-if="!forClaim"
          class="item_location_select__meta"
        >
          {{ option.locationCode }}{{ option.enabled === 0 ? ' · 已停用' : '' }}
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
  disabled: { type: Boolean, default: false },
  clearable: { type: Boolean, default: true },
  forClaim: { type: Boolean, default: false },
  currentOption: { type: Object, default: null },
  placeholder: { type: String, default: '搜索并选择库位' }
})

const emit = defineEmits(['update:modelValue', 'change'])

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
  return props.forClaim
    ? option.locationName || ''
    : [option.locationName, option.locationCode].filter(Boolean).join(' · ')
}

watch(
  () => props.currentOption,
  (option) => {
    if (!option?.id) return
    const normalized = { ...option, id: String(option.id) }
    selectedOption.value = normalized
    options.value = [normalized, ...options.value.filter((item) => item.id !== normalized.id)]
  },
  { immediate: true }
)

async function loadOptions(keyword = '') {
  const serial = ++requestSerial
  loading.value = true
  try {
    const result = await request.get(props.forClaim ? '/item/locations/options' : '/item/locations', {
      params: props.forClaim
        ? { keyword: keyword || undefined }
        : {
            pageNum: 1,
            pageSize: 100,
            locationName: keyword || undefined
          }
    })
    if (serial !== requestSerial) return
    const records = props.forClaim ? result || [] : result?.records || []
    const normalized = records.map((item) => ({ ...item, id: String(item.id) }))
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

onMounted(() => loadOptions())
onUnmounted(() => {
  window.clearTimeout(searchTimer)
  requestSerial += 1
})
</script>

<style scoped>
.item_location_select {
  width: 100%;
}

.item_location_select__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.item_location_select__meta {
  overflow: hidden;
  font-size: 12px;
  color: var(--g3);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
