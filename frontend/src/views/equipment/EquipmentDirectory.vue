<template>
  <div class="page equipment_directory">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          设备目录
        </h2>
        <p class="t-sub">
          在架可申请设备一览（按设备分类分组）· 学生单由指定教师初审，通过即生效
        </p>
      </div>
    </div>

    <div class="card">
      <div class="equipment_directory__toolbar">
        <el-input
          v-model="query.keyword"
          class="equipment_directory__filter equipment_directory__filter--keyword"
          placeholder="资产编号 / 序列号 / 名称"
          clearable
          @keyup.enter="search"
          @clear="search"
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
        v-if="loading && !records.length"
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
            <span class="equip_group__count">{{ group.assets.length }} 台设备</span>
          </div>
          <div
            v-show="expanded[group.categoryId]"
            class="equip_group__body"
          >
            <div class="table-scroll-x">
              <el-table :data="group.assets">
                <el-table-column
                  prop="assetCode"
                  label="资产编号"
                  min-width="110"
                />
                <el-table-column
                  prop="equipmentName"
                  label="设备名称"
                  min-width="150"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="modelCode"
                  label="型号码"
                  min-width="110"
                />
                <el-table-column
                  prop="departmentName"
                  label="归口部门"
                  min-width="110"
                />
                <el-table-column
                  label="存放位置"
                  min-width="140"
                  show-overflow-tooltip
                >
                  <template #default="{ row }">
                    {{ row.locationDetail || '—' }}
                  </template>
                </el-table-column>
                <el-table-column
                  label="保管人"
                  width="90"
                >
                  <template #default="{ row }">
                    {{ row.custodianName || '—' }}
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

    <!-- 申请使用（共享弹窗：目录行入口预选资产） -->
    <BookingApplyDialog
      ref="applyDialogRef"
      :preset="applyPreset"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ArrowDown, Collection, Monitor } from '@element-plus/icons-vue'
import { categoryTree, pageAssets } from '@/api/equipment'
import BookingApplyDialog from '@/components/BookingApplyDialog.vue'

/**
 * 设备目录（学生/教师用户端）：只读在架目录（服务端收敛为 IDLE 且启用视图），
 * 全量加载不分页，按设备分类分组折叠（借鉴管理员端设备管理：分类树筛选 + 分组展示）；
 * 「申请使用」打开共享申请弹窗（预选当前行设备）。
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

/* ---------------- 列表（按分类分组） ---------------- */

/** 当前页记录按所属分类分组（组名取行内一致的分类名） */
const groups = computed(() => {
  const order = []
  const map = new Map()
  for (const row of records.value) {
    const key = String(row.categoryId ?? 'none')
    if (!map.has(key)) {
      map.set(key, {
        categoryId: key,
        categoryName: row.categoryName || '未分类',
        assets: []
      })
      order.push(key)
    }
    map.get(key).assets.push(row)
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

/** 全量加载：后端 pageSize 上限 100，循环翻页直至收齐 total 条（目录不分页） */
async function load() {
  loading.value = true
  try {
    const base = { pageSize: 100 }
    if (query.keyword.trim()) {
      base.keyword = query.keyword.trim()
    }
    if (query.categoryId) {
      base.categoryId = query.categoryId
    }
    const all = []
    let total = Infinity
    let pageNum = 1
    while (all.length < total) {
      const data = await pageAssets({ ...base, pageNum })
      const batch = data.records || []
      all.push(...batch)
      total = data.total || 0
      pageNum += 1
      if (!batch.length) {
        break
      }
    }
    records.value = all
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

function search() {
  load()
}

/* ---------------- 申请使用 ---------------- */

const applyDialogRef = ref()
const applyPreset = ref(null)

function openApply(row) {
  applyPreset.value = {
    id: row.id,
    assetCode: row.assetCode,
    equipmentName: row.equipmentName
  }
  applyDialogRef.value?.open()
}

onMounted(() => {
  load()
  loadCategoryTree()
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
