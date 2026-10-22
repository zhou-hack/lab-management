<template>
  <el-select
    v-model="model"
    filterable
    remote
    clearable
    style="min-width: 180px"
    popper-class="item-select-popper"
    :remote-method="searchOptions"
    :loading="searching"
    :placeholder="placeholder"
  >
    <el-option
      v-if="allOption"
      :label="allLabel"
      value=""
    />
    <el-option
      v-for="s in options"
      :key="s.id"
      :label="`${s.skuCode} · ${s.materialName}`"
      :value="s.id"
    />
  </el-select>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { getMaterialSku, pageSkus } from '@/api/item'

/**
 * 物料远程搜索选择器
 * 下拉按关键词远程搜索（一次 20 条），替代一次性预取全量物料；
 * 编辑场景预置 ID 时自动拉取详情回显名称；
 * 工具栏筛选场景传 allOption 在首部显示「全部」选项（选中即清空过滤）
 */
const model = defineModel({ type: String, default: '' })
defineProps({
  placeholder: { type: String, default: '输入编码/名称搜索物料' },
  allOption: { type: Boolean, default: false },
  allLabel: { type: String, default: '全部物料' }
})

const options = ref([])
const searching = ref(false)

async function searchOptions(keyword) {
  searching.value = true
  try {
    const data = await pageSkus({ pageNum: 1, pageSize: 20, keyword: keyword || undefined })
    options.value = data.records.filter((s) => s.enabled)
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
    const sku = await getMaterialSku(model.value)
    if (!options.value.some((o) => o.id === sku.id)) {
      options.value = [sku, ...options.value]
    }
  } catch {
    // 档案可能已删除，保持原值不处理
  }
}

watch(model, resolvePreset)

onMounted(() => {
  searchOptions('')
  resolvePreset()
})
</script>
