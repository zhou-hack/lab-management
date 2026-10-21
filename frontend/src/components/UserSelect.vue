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
      v-for="u in options"
      :key="u.userId"
      :label="optionLabel(u)"
      :value="u.userId"
    />
  </el-select>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { custodianOptions } from '@/api/item'

/**
 * 用户远程搜索选择器（跨模块只读，当前用于库位保管人维护）
 * 与 SkuSelect / LocationSelect / CategorySelect 同构：
 * 按姓名/账号/学工号前缀远程搜索；编辑场景传入 presetName 回显，避免列表反查
 */
const model = defineModel({ type: String, default: '' })
const props = defineProps({
  placeholder: { type: String, default: '输入姓名/账号搜索用户' },
  presetName: { type: String, default: '' }
})

const options = ref([])
const searching = ref(false)

function optionLabel(u) {
  const tail = u.personNo || u.accountName || ''
  return tail ? `${u.realName}（${tail}）` : u.realName
}

async function searchOptions(keyword) {
  searching.value = true
  try {
    options.value = await custodianOptions(keyword || undefined)
  } catch {
    options.value = []
  } finally {
    searching.value = false
  }
}

// 编辑回显：模型值不在候选中且带 presetName 时，用名称预置一条选项
watch(
  () => [model.value, props.presetName],
  () => {
    if (model.value && props.presetName && !options.value.some((o) => o.userId === model.value)) {
      options.value = [{ userId: model.value, realName: props.presetName, accountName: '', personNo: '' }]
    }
  },
  { immediate: true }
)

onMounted(() => {
  searchOptions('')
})
</script>
