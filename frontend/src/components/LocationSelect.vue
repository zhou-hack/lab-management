<template>
  <el-select
    v-model="model"
    filterable
    remote
    clearable
    :remote-method="searchOptions"
    :loading="searching"
    :placeholder="placeholder"
  >
    <el-option
      v-for="l in options"
      :key="l.id"
      :label="`${l.locationCode} · ${l.locationName}`"
      :value="l.id"
    />
  </el-select>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { getStockLocation, pageLocations } from '@/api/item'

/**
 * 库位远程搜索选择器
 * 下拉按关键词远程搜索（一次 20 条），替代一次性预取全量库位；
 * 编辑场景预置 ID 时自动拉取详情回显名称
 */
const model = defineModel({ type: String, default: '' })
defineProps({
  placeholder: { type: String, default: '输入编码/名称搜索库位' }
})

const options = ref([])
const searching = ref(false)

async function searchOptions(keyword) {
  searching.value = true
  try {
    const data = await pageLocations({ pageNum: 1, pageSize: 20, keyword: keyword || undefined })
    options.value = data.records.filter((l) => l.enabled)
  } catch {
    options.value = []
  } finally {
    searching.value = false
  }
}

/** 预置值回显：模型值不在当前选项中时拉取详情补充 */
async function resolvePreset() {
  if (!model.value || options.value.some((o) => o.id === model.value)) return
  try {
    const location = await getStockLocation(model.value)
    if (!options.value.some((o) => o.id === location.id)) {
      options.value = [location, ...options.value]
    }
  } catch {
    // 库位可能已删除，保持原值不处理
  }
}

watch(model, resolvePreset)

onMounted(() => {
  searchOptions('')
  resolvePreset()
})
</script>
