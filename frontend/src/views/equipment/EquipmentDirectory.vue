<template>
  <div class="page equipment_directory">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          设备目录
        </h2>
        <p class="t-sub">
          在架可申请设备一览（按型号合并，相同设备只显示一台及在架台数）· 学生单由指定教师初审，通过即生效
        </p>
      </div>
    </div>

    <div class="card">
      <div class="equipment_directory__toolbar">
        <el-autocomplete
          v-model="query.keyword"
          class="equipment_directory__filter equipment_directory__filter--keyword"
          :fetch-suggestions="querySuggestions"
          placeholder="型号 / 设备名称（输入显示可选关键字）"
          clearable
          @select="onSuggestionSelect"
          @keyup.enter="search"
        />
        <el-tree-select
          v-model="query.categoryId"
          class="equipment_directory__filter"
          :data="categoryFilterOptions"
          node-key="id"
          check-strictly
          clearable
          placeholder="全部分类"
          @change="search"
        />
        <el-button @click="search">
          查询
        </el-button>
      </div>
    </div>

    <div class="card">
      <div
        v-if="loading && !groups.length"
        class="equipment_directory__skeleton"
      >
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row equipment_directory__sk_row--indent" />
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row equipment_directory__sk_row--indent" />
      </div>
      <template v-else-if="groups.length">
        <!-- 按设备分类分组折叠（借鉴管理员端设备管理分类方法） -->
        <div
          v-for="group in groups"
          :key="group.categoryId"
          class="equip_group"
          :class="{ 'equip_group--collapsed': !expanded[group.categoryId] }"
        >
          <div
            class="equip_group__head"
            role="button"
            tabindex="0"
            @click="toggleGroup(group.categoryId)"
            @keydown.enter.prevent="toggleGroup(group.categoryId)"
            @keydown.space.prevent="toggleGroup(group.categoryId)"
          >
            <el-icon class="equip_group__chevron">
              <ArrowDown />
            </el-icon>
            <span class="equip_group__icon">
              <el-icon :size="14">
                <Collection />
              </el-icon>
            </span>
            <span class="equip_group__name">{{ group.categoryName }}</span>
            <span class="equip_group__count">在架 {{ group.onShelfCount }} 台</span>
          </div>
          <div
            v-show="expanded[group.categoryId]"
            class="equip_group__body"
          >
            <div class="table-scroll-x">
              <el-table :data="group.models">
                <el-table-column
                  prop="equipmentName"
                  label="设备名称"
                  min-width="160"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="modelCode"
                  label="型号码"
                  min-width="110"
                />
                <el-table-column
                  label="在架台数"
                  width="90"
                >
                  <template #default="{ row }">
                    {{ onShelfCount(row) }} 台
                  </template>
                </el-table-column>
                <el-table-column
                  label="借出中（资产编号）"
                  min-width="200"
                >
                  <template #default="{ row }">
                    <span v-if="row.checkedOutCodes && row.checkedOutCodes.length">
                      {{ row.checkedOutCodes.join('、') }}
                    </span>
                    <span
                      v-else
                      class="t-hint"
                    >—</span>
                  </template>
                </el-table-column>
                <el-table-column
                  label="借出台数"
                  width="90"
                >
                  <template #default="{ row }">
                    {{ checkedOutCount(row) }} 台
                  </template>
                </el-table-column>
                <el-table-column
                  label="申请使用次数"
                  width="110"
                >
                  <template #default="{ row }">
                    {{ applyableCount(row) }} 台
                  </template>
                </el-table-column>
                <el-table-column
                  label="操作"
                  width="110"
                  fixed="right"
                >
                  <template #default="{ row }">
                    <el-button
                      v-permission="'equipment:booking:apply'"
                      link
                      type="primary"
                      :disabled="applyableCount(row) === 0"
                      @click="openApply(row)"
                    >
                      申请使用
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
      </template>
      <div
        v-else
        class="empty"
      >
        <el-icon
          class="empty__icon"
          :size="40"
        >
          <Monitor />
        </el-icon>
        暂无在架设备
      </div>
    </div>

    <!-- 申请使用（共享弹窗：目录行入口预选首个在架资产） -->
    <BookingApplyDialog
      ref="applyDialogRef"
      :preset="applyPreset"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ArrowDown, Collection, Monitor } from '@element-plus/icons-vue'
import { categoryTree, directoryAssets } from '@/api/equipment'
import BookingApplyDialog from '@/components/BookingApplyDialog.vue'

/**
 * 设备目录（学生/教师用户端）：服务端按型号聚合（相同型号设备合并展示），
 * 全量加载不分页，按设备分类分组折叠；
 * 不逐台暴露在架资产编号（仅显示在架台数），借出中的设备展示其资产编号；
 * 另列借出台数（借出中台数）与申请使用次数：在架台数为该型号在库总台数（空闲+借出，
 * 借出/归还不变），申请使用次数 = 在架台数 - 借出台数（不小于 0；为 0 时申请按钮置灰
 * 禁用，不可再申请）；归还一台：借出台数 -1、申请使用次数 +1；
 * 搜索框带关键字联想（设备内已有的型号编码/设备名称，点击建议即查询）；
 * 「申请使用」打开共享申请弹窗（预选该型号首个在架资产）。
 */
const query = reactive({
  keyword: '',
  categoryId: null
})
const records = ref([])
const loading = ref(false)

/* ---------------- 分类筛选（分类树下拉） ---------------- */

const categoryTreeData = ref([])

async function loadCategoryTree() {
  try {
    categoryTreeData.value = (await categoryTree()) || []
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/** 克隆为 el-tree-select 数据：分类名（编码）标签 */
function cloneLabeled(nodes) {
  const result = []
  for (const node of nodes || []) {
    result.push({
      id: node.id,
      label: `${node.categoryName}（${node.categoryCode}）`,
      children: cloneLabeled(node.children)
    })
  }
  return result
}

const categoryFilterOptions = computed(() => cloneLabeled(categoryTreeData.value))

/* ---------------- 列表（按分类分组，组内为型号合并行） ---------------- */

/** 型号聚合记录按所属分类分组（组内台数为在架合计，含借出） */
const groups = computed(() => {
  const order = []
  const map = new Map()
  for (const row of records.value) {
    const key = String(row.categoryId ?? 'none')
    if (!map.has(key)) {
      map.set(key, {
        categoryId: key,
        categoryName: row.categoryName || '未分类',
        onShelfCount: 0,
        models: []
      })
      order.push(key)
    }
    const group = map.get(key)
    group.onShelfCount += onShelfCount(row)
    group.models.push(row)
  }
  return order.map((key) => map.get(key))
})

/* 分组折叠状态：新分组默认展开 */
const expanded = reactive({})

watch(
  groups,
  (list) => {
    for (const group of list) {
      if (!(group.categoryId in expanded)) {
        expanded[group.categoryId] = true
      }
    }
  },
  { immediate: true }
)

function toggleGroup(key) {
  expanded[key] = !expanded[key]
}

/** 目录聚合：一次请求返回全量（服务端不分页） */
async function load() {
  loading.value = true
  try {
    const params = {}
    if (query.keyword.trim()) {
      params.keyword = query.keyword.trim()
    }
    if (query.categoryId) {
      params.categoryId = query.categoryId
    }
    records.value = (await directoryAssets(params)) || []
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

function search() {
  load()
}

/* ---------------- 关键字联想（设备内已有的型号编码/设备名称） ---------------- */

/** 联想数据源：设备内全量型号（无筛选），一次加载常驻用于联想 */
const allModels = ref([])

async function loadSuggestionSource() {
  try {
    allModels.value = (await directoryAssets({})) || []
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/** 输入时按前缀/包含匹配设备内已有的型号编码与设备名称，最多返回 10 条 */
function querySuggestions(text, cb) {
  const keyword = (text || '').trim().toLowerCase()
  if (!keyword) {
    cb([])
    return
  }
  const seen = new Set()
  const items = []
  for (const model of allModels.value) {
    for (const value of [model.modelCode, model.equipmentName]) {
      if (!value) {
        continue
      }
      const key = value.toLowerCase()
      if (key.includes(keyword) && !seen.has(key)) {
        seen.add(key)
        items.push({ value })
        if (items.length >= 10) {
          cb(items)
          return
        }
      }
    }
  }
  cb(items)
}

/** 点击建议项：回填关键字并触发查询 */
function onSuggestionSelect(item) {
  query.keyword = item.value
  search()
}

/** 清空关键字（点击清除/退格删空）时恢复全量目录 */
watch(
  () => query.keyword,
  (value) => {
    if (!value) {
      search()
    }
  }
)

/* ---------------- 申请使用 ---------------- */

const applyDialogRef = ref()
const applyPreset = ref(null)

/** 借出台数（该型号借出中台数） */
function checkedOutCount(row) {
  return Array.isArray(row.checkedOutCodes) ? row.checkedOutCodes.length : 0
}

/** 在架台数 = 该型号在库台数（在架空闲 + 借出中）；借出/归还不改变总数 */
function onShelfCount(row) {
  return (Number(row.idleCount) || 0) + checkedOutCount(row)
}

/**
 * 可申请台数 = 在架台数 - 借出台数（不小于 0；为 0 时按钮置灰禁用）。
 * 归还一台设备：借出台数 -1 → 申请使用次数 +1；借出一台：借出台数 +1 → 申请使用次数 -1。
 */
function applyableCount(row) {
  return Math.max(0, onShelfCount(row) - checkedOutCount(row))
}

function openApply(row) {
  applyPreset.value = {
    id: row.firstIdleAssetId,
    assetCode: row.firstIdleAssetCode,
    equipmentName: row.equipmentName
  }
  applyDialogRef.value?.open()
}

onMounted(() => {
  load()
  loadCategoryTree()
  loadSuggestionSource()
})
</script>

<style scoped>
.equipment_directory__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.equipment_directory__filter {
  width: 200px;
}

/* 加载骨架：模拟分组行位（规范 7.8） */
.equipment_directory__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 2px 10px;
}

.equipment_directory__sk_row {
  height: 36px;
  border-radius: var(--r-sm);
}

.equipment_directory__sk_row--indent {
  width: 86%;
  margin-left: 38px;
}
</style>
