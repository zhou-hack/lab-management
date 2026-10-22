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
      v-for="c in options"
      :key="c.id"
      :label="`${c.categoryCode} · ${c.categoryName}`"
      :value="c.id"
    />
  </el-select>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { getCategory, pageCategories } from '@/api/item'

/**
 * 物料分类远程搜索选择器（Typeahead）
 * 下拉按关键词远程搜索（一次 20 条，仅启用分类），替代一次性预取全量分类；
 * 编辑场景预置 ID 时自动拉取详情回显名称；excludeId 供父分类选择防环；
 * 工具栏筛选场景传 allOption 在首部显示「全部」选项（选中即清空过滤）
 */
const model = defineModel({ type: String, default: '' })
const props = defineProps({
  placeholder: { type: String, default: '输入编码/名称搜索分类' },
  allOption: { type: Boolean, default: false },
  allLabel: { type: String, default: '全部分类' },
  excludeId: { type: String, default: '' }
})

const options = ref([])
const searching = ref(false)

async function searchOptions(keyword) {
  searching.value = true
  try {
    const data = await pageCategories({
      pageNum: 1,
      pageSize: 20,
      keyword: keyword || undefined,
      enabled: true
    })
    options.value = data.records.filter((c) => c.enabled && c.id !== props.excludeId)
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
    const category = await getCategory(model.value)
    if (!options.value.some((o) => o.id === category.id) && category.id !== props.excludeId) {
      options.value = [category, ...options.value]
    }
  } catch {
    // 分类可能已删除，保持原值不处理
  }
}

watch(model, resolvePreset)

onMounted(() => {
  searchOptions('')
  resolvePreset()
})
</script>
