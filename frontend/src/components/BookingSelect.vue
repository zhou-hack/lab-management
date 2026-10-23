<template>
  <el-select
    v-model="model"
    filterable
    remote
    clearable
    style="min-width: 220px"
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
      v-for="o in visibleOptions"
      :key="o.id"
      :label="optionLabel(o)"
      :value="o.id"
    >
      <div>
        <div>
          {{ optionLabel(o) }}
          <el-tag
            size="small"
            :type="statusOf(bookingStatusMap, o.status).type"
          >
            {{ statusOf(bookingStatusMap, o.status).label }}
          </el-tag>
        </div>
        <div class="muted small">
          {{ secondaryLine(o) }}
        </div>
      </div>
    </el-option>
  </el-select>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { bookingOptions } from '@/api/item'
import { bookingStatusMap, statusOf } from '@/utils/statusMap'
import { fmtShanghai } from '@/utils/format'

/**
 * 场地预约单远程搜索选择器（新建物料预约选择关联预约用）
 * 下拉按关键字远程搜索可关联（待审批/已批准）预约单，一次 20 条；
 * 选中后缓存选项信息，重新搜索结果不含该行时置顶补显，保证标签回显
 */
const model = defineModel({ type: String, default: '' })
defineProps({
  placeholder: { type: String, default: '输入场地/课程/班级/用途搜索预约单' },
  allOption: { type: Boolean, default: false },
  allLabel: { type: String, default: '全部预约单' }
})

const options = ref([])
const searching = ref(false)
/** 已选中项缓存（含场地/时间等展示信息） */
const selected = ref(null)

/** 搜索结果不含已选项时置顶补显，避免选中后标签退化为裸 ID */
const visibleOptions = computed(() => {
  if (!selected.value || model.value !== selected.value.id) return options.value
  return options.value.some((o) => o.id === selected.value.id)
    ? options.value
    : [selected.value, ...options.value]
})

async function searchOptions(keyword) {
  searching.value = true
  try {
    options.value = await bookingOptions(keyword)
  } catch {
    options.value = []
  } finally {
    searching.value = false
  }
}

/** 选中即缓存完整选项，供后续搜索刷新后回显标签 */
watch(model, (val) => {
  if (!val) {
    selected.value = null
    return
  }
  const hit = options.value.find((o) => o.id === val)
  if (hit) selected.value = hit
})

function optionLabel(o) {
  return `${o.labName} · ${o.bookingDate}`
}

/** 次行信息：课程 · 班级 · 用途 + 上海时区起止时间 */
function secondaryLine(o) {
  const head = [o.courseName, o.className, o.purpose].filter(Boolean).join(' · ')
  const range = `${fmtShanghai(o.startAt)} ~ ${fmtShanghai(o.endAt)}`
  return head ? `${head} · ${range}` : range
}

onMounted(() => {
  searchOptions('')
})
</script>
