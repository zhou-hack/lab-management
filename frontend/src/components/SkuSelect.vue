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
      :label="forRequest
        ? `${s.skuCode} · ${s.materialName}（库存 ${formatStock(s.availableQty)} ${s.baseUnit}）`
        : `${s.skuCode} · ${s.materialName}`"
      :value="s.id"
    />
  </el-select>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { getMaterialSku, pageSkus, skuOptions } from '@/api/item'

/**
 * 物料远程搜索选择器
 * 下拉按关键词远程搜索（一次 20 条），替代一次性预取全量物料；
 * 编辑场景预置 ID 时自动拉取详情回显名称；
 * 工具栏筛选场景传 allOption 在首部显示「全部」选项（选中即清空过滤）
 */
const model = defineModel({ type: String, default: '' })
const props = defineProps({
  placeholder: { type: String, default: '输入编码/名称搜索物料' },
  allOption: { type: Boolean, default: false },
  allLabel: { type: String, default: '全部物料' },
  /**
   * 申领表单模式：改走轻量 /material-skus/options（item:request:create 权限），
   * 供无 item:material:list 档案权限的老师/学生选择物料；管理端筛选保持 pageSkus
   */
  forRequest: { type: Boolean, default: false }
})

const options = ref([])
const searching = ref(false)

async function searchOptions(keyword) {
  searching.value = true
  try {
    const text = (keyword || '').trim()
    if (props.forRequest) {
      // 用户端申领：单 keyword 由后端同时模糊匹配 SKU 码与名称
      const list = await skuOptions(text || undefined)
      options.value = list || []
      return
    }
    if (!text) {
      const data = await pageSkus({ pageNum: 1, pageSize: 20 })
      options.value = data.records.filter((s) => s.enabled)
      return
    }
    // SKU 查询参数已按字段拆分（skuCode / materialName 均模糊匹配，AND 组合）：
    // 单框搜索体验 = 两路并发各查一次，合并去重
    const [byCode, byName] = await Promise.all([
      pageSkus({ pageNum: 1, pageSize: 20, skuCode: text }),
      pageSkus({ pageNum: 1, pageSize: 20, materialName: text })
    ])
    const seen = new Set()
    const merged = []
    for (const row of [...(byCode.records || []), ...(byName.records || [])]) {
      if (!seen.has(row.id)) {
        seen.add(row.id)
        merged.push(row)
      }
    }
    options.value = merged.filter((s) => s.enabled).slice(0, 20)
  } catch {
    options.value = []
  } finally {
    searching.value = false
  }
}

/** 库存量展示：去尾零（DECIMAL(18,3) 序列化可能带小数尾零） */
function formatStock(qty) {
  if (qty === null || qty === undefined) return '0'
  const n = Number(qty)
  return Number.isInteger(n) ? String(n) : String(n)
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
