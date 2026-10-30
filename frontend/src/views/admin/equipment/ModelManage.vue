<template>
  <div class="equip_section model_manage">
    <div class="card">
      <div class="model_manage__toolbar">
        <div class="model_manage__search">
          <el-autocomplete
            v-model="query.modelCode"
            :fetch-suggestions="suggestModelCodes"
            placeholder="型号码"
            clearable
            @keyup.enter="search"
            @clear="search"
            @select="search"
          />
        </div>
        <div class="model_manage__search">
          <el-autocomplete
            v-model="query.equipmentName"
            :fetch-suggestions="suggestEquipmentNames"
            placeholder="设备名称"
            clearable
            @keyup.enter="search"
            @clear="search"
            @select="search"
          />
        </div>
        <el-tree-select
          v-model="query.categoryId"
          class="model_manage__filter"
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
        <el-button
          v-permission="['equipment:category:create', 'equipment:category:update', 'equipment:category:delete']"
          class="model_manage__toolbar_category"
          @click="categoryDrawerVisible = true"
        >
          管理分类
        </el-button>
        <el-button
          v-permission="'equipment:model:create'"
          type="primary"
          class="model_manage__toolbar_action"
          @click="openCreate()"
        >
          新增型号
        </el-button>
      </div>
    </div>

    <div class="card">
      <!-- 加载骨架（规范 7.8）：模拟分组行位，避免白屏与纯 spinner -->
      <div
        v-if="loading"
        class="model_manage__skeleton"
      >
        <span class="skeleton model_manage__sk_row" />
        <span class="skeleton model_manage__sk_row model_manage__sk_row--indent" />
        <span class="skeleton model_manage__sk_row model_manage__sk_row--indent" />
        <span class="skeleton model_manage__sk_row" />
        <span class="skeleton model_manage__sk_row" />
      </div>
      <template v-else-if="groups.length">
        <div
          v-for="group in pagedGroups"
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
            <span
              v-if="group.meta"
              class="equip_group__badge"
            >{{ group.meta.code }}</span>
            <span
              v-if="group.meta && group.meta.enabled !== 1"
              class="pill pill--muted"
            >
              已停用
            </span>
            <span class="equip_group__count">{{ group.models.length }} 个型号</span>
            <span
              v-if="!group.meta || group.meta.enabled === 1"
              class="equip_group__actions"
            >
              <el-button
                v-permission="'equipment:model:create'"
                link
                type="primary"
                size="small"
                @click.stop="openCreate(group)"
              >
                新增型号
              </el-button>
            </span>
          </div>
          <div
            v-show="expanded[group.categoryId]"
            class="equip_group__body"
          >
            <div class="table-scroll-x">
              <el-table :data="group.models">
                <el-table-column
                  prop="modelCode"
                  label="型号码"
                  min-width="110"
                />
                <el-table-column
                  prop="equipmentName"
                  label="设备名称"
                  min-width="150"
                />
                <el-table-column
                  label="品牌"
                  min-width="100"
                >
                  <template #default="{ row }">
                    {{ row.brand || '—' }}
                  </template>
                </el-table-column>
                <el-table-column
                  prop="specification"
                  label="规格说明"
                  min-width="200"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="quantity"
                  label="数量"
                  width="90"
                >
                  <template #default="{ row }">
                    {{ row.quantity ?? 0 }}
                  </template>
                </el-table-column>
                <el-table-column
                  prop="unitName"
                  label="计量单位"
                  width="90"
                />
                <el-table-column
                  label="参考价格"
                  width="110"
                >
                  <template #default="{ row }">
                    {{ row.referencePrice ?? '—' }}
                  </template>
                </el-table-column>
                <el-table-column
                  label="设备状态"
                  width="100"
                >
                  <template #default="{ row }">
                    <el-button
                      link
                      type="primary"
                      @click="openAssets(row)"
                    >
                      查看详情
                    </el-button>
                  </template>
                </el-table-column>
                <el-table-column
                  label="操作"
                  width="120"
                  fixed="right"
                >
                  <template #default="{ row }">
                    <el-button
                      v-permission="'equipment:model:update'"
                      link
                      type="primary"
                      @click="openEdit(row)"
                    >
                      编辑
                    </el-button>
                    <el-button
                      v-permission="'equipment:model:delete'"
                      link
                      type="danger"
                      @click="removeModel(row)"
                    >
                      删除
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
          <Box />
        </el-icon>
        暂无型号，请从「新增型号」开始建立
      </div>
      <el-pagination
        v-if="groups.length"
        v-model:current-page="groupPageNum"
        class="model_manage__pager"
        :page-size="GROUP_PAGE_SIZE"
        :total="groups.length"
        layout="total, prev, pager, next"
      />
    </div>

    <!-- 分类目录管理抽屉：原分类管理页并入型号面板 -->
    <el-drawer
      v-model="categoryDrawerVisible"
      title="分类目录管理"
      size="600px"
      destroy-on-close
      append-to-body
    >
      <CategoryDirectory @changed="onCategoryChanged" />
    </el-drawer>

    <!-- 设备明细：该型号下的每台设备（台账分页） -->
    <el-dialog
      v-model="assetsDialog.visible"
      :title="`设备明细 · ${assetsDialog.modelText}`"
      width="960px"
      destroy-on-close
    >
      <el-table
        v-loading="assetsDialog.loading"
        :data="assetsDialog.records"
        class="table-scroll-x"
      >
        <el-table-column
          prop="assetCode"
          label="资产编号"
          min-width="110"
        />
        <el-table-column
          label="序列号"
          min-width="110"
        >
          <template #default="{ row }">
            {{ row.serialNo || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="labName"
          label="保管实验室"
          min-width="130"
        >
          <template #default="{ row }">
            {{ row.labName || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="departmentName"
          label="归口部门"
          min-width="100"
        />
        <el-table-column
          label="存放位置"
          min-width="120"
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
          label="价值分级"
          width="90"
        >
          <template #default="{ row }">
            <span
              class="pill"
              :class="valueLevelMeta(row).pill"
            >
              {{ valueLevelMeta(row).label }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="90"
        >
          <template #default="{ row }">
            <span
              class="pill"
              :class="enumMeta(row.status, EQUIPMENT_STATUS).pill"
            >
              {{ enumMeta(row.status, EQUIPMENT_STATUS).label }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="assetsDialog.pageNum"
        v-model:page-size="assetsDialog.pageSize"
        class="model_manage__pager"
        :total="assetsDialog.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="loadAssets"
      />
    </el-dialog>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增型号' : '编辑型号'"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item
          label="所属分类"
          prop="categoryId"
        >
          <el-tree-select
            v-model="form.categoryId"
            :data="categoryFormOptions"
            node-key="id"
            check-strictly
            :disabled="dialog.mode === 'edit'"
            placeholder="请选择分类"
          />
        </el-form-item>
        <el-form-item
          label="型号码"
          prop="modelCode"
        >
          <el-input
            v-model="form.modelCode"
            :disabled="dialog.mode === 'edit'"
            maxlength="64"
            placeholder="如 MODEL-A100（创建后不可改）"
          />
        </el-form-item>
        <el-form-item
          label="设备名称"
          prop="equipmentName"
        >
          <el-input
            v-model="form.equipmentName"
            maxlength="128"
            placeholder="如 ThinkPad T14"
          />
        </el-form-item>
        <el-form-item label="品牌">
          <el-input
            v-model="form.brand"
            maxlength="128"
            placeholder="选填"
          />
        </el-form-item>
        <el-form-item
          label="规格说明"
          prop="specification"
        >
          <el-input
            v-model="form.specification"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="如 i7/16G/512G"
          />
        </el-form-item>
        <el-form-item
          label="数量"
          prop="quantity"
        >
          <el-input-number
            v-model="form.quantity"
            :min="0"
            :precision="0"
          />
        </el-form-item>
        <el-form-item
          label="计量单位"
          prop="unitName"
        >
          <el-input
            v-model="form.unitName"
            maxlength="16"
            placeholder="如 台"
          />
        </el-form-item>
        <el-form-item label="参考价格">
          <el-input-number
            v-model="form.referencePrice"
            :min="0"
            :precision="2"
          />
        </el-form-item>
        <el-form-item label="预期寿命（月）">
          <el-input-number
            v-model="form.expectedLifeMonths"
            :min="0"
            :precision="0"
          />
        </el-form-item>
        <el-form-item label="归还须数据擦除">
          <el-switch v-model="form.requiresDataErasure" />
        </el-form-item>
        <el-form-item
          v-if="dialog.mode === 'edit'"
          label="启用状态"
        >
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="dialog.saving"
          @click="save"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Box, Collection } from '@element-plus/icons-vue'
import { categoryTree, createModel, deleteModel, pageAssets, pageModels, updateModel } from '@/api/equipment'
import { EQUIPMENT_STATUS, VALUE_LEVEL, enumMeta } from '@/utils/enums'
import CategoryDirectory from '@/views/admin/equipment/CategoryManage.vue'

/**
 * 设备型号段（EquipmentManage 折叠面板子组件）。
 * - 当前页记录按所属分类分组折叠展示；「管理分类」抽屉内嵌分类目录 CRUD（原分类页并入）
 * - active 由父级面板展开状态驱动：首次展开才加载数据；收起时 DOM 保留、状态不丢失
 */
const props = defineProps({
  active: { type: Boolean, default: false }
})

const emit = defineEmits(['count'])

/* ---------------- 分类树 ---------------- */

const categoryTreeData = ref([])
const loaded = ref(false)

async function loadCategoryTree() {
  try {
    categoryTreeData.value = (await categoryTree()) || []
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/** 克隆为 el-tree-select 数据；onlyEnabled 控制是否仅保留启用节点（挂靠新型号时用） */
function cloneLabeled(nodes, onlyEnabled) {
  const result = []
  for (const node of nodes || []) {
    if (onlyEnabled && node.enabled !== 1) {
      continue
    }
    result.push({
      id: node.id,
      label: `${node.categoryName}（${node.categoryCode}）`,
      children: cloneLabeled(node.children, onlyEnabled)
    })
  }
  return result
}

/** 筛选下拉：全部分类（含停用，便于按历史分类检索） */
const categoryFilterOptions = computed(() => cloneLabeled(categoryTreeData.value, false))
/** 表单下拉：仅启用分类可挂靠新型号 */
const categoryFormOptions = computed(() => cloneLabeled(categoryTreeData.value, true))

/** 分类元数据（编码/启用），供分组头徽章与状态展示 */
const categoryMeta = computed(() => {
  const map = {}
  const walk = (nodes) => {
    for (const node of nodes || []) {
      map[String(node.id)] = { code: node.categoryCode, enabled: node.enabled }
      walk(node.children)
    }
  }
  walk(categoryTreeData.value)
  return map
})

/* ---------------- 列表（按分类分组） ---------------- */

const query = reactive({ modelCode: '', equipmentName: '', categoryId: null })
const records = ref([])
const loading = ref(false)

/** 当前页记录按所属分类分组（保持记录出现顺序） */
const groups = computed(() => {
  const order = []
  const map = new Map()
  for (const row of records.value) {
    const key = String(row.categoryId ?? 'none')
    if (!map.has(key)) {
      map.set(key, {
        categoryId: key,
        categoryName: row.categoryName || '未分组',
        meta: categoryMeta.value[key],
        models: []
      })
      order.push(key)
    }
    map.get(key).models.push(row)
  }
  return order.map((key) => map.get(key))
})

/* ---------------- 分组分页：每页 10 类设备（分类分组） ---------------- */

const GROUP_PAGE_SIZE = 10
const groupPageNum = ref(1)

/** 当前页的分类分组切片；折叠状态按 categoryId 保持，跨页不丢失 */
const pagedGroups = computed(() => {
  const start = (groupPageNum.value - 1) * GROUP_PAGE_SIZE
  return groups.value.slice(start, start + GROUP_PAGE_SIZE)
})

/* 筛选/删除后分组数变少时收敛页码，避免停留在空白页 */
watch(
  () => groups.value.length,
  (len) => {
    const maxPage = Math.max(1, Math.ceil(len / GROUP_PAGE_SIZE))
    if (groupPageNum.value > maxPage) {
      groupPageNum.value = maxPage
    }
  }
)

/* 分组折叠状态：新分组默认折叠，点击展开 */
const expanded = reactive({})

watch(
  groups,
  (list) => {
    for (const group of list) {
      if (!(group.categoryId in expanded)) {
        expanded[group.categoryId] = false
      }
    }
  },
  { immediate: true }
)

function toggleGroup(key) {
  expanded[key] = !expanded[key]
}

/** 全量加载：后端 pageSize 上限 100，循环翻页直至收齐 total 条 */
async function load() {
  loading.value = true
  try {
    const base = { pageSize: 100 }
    if (query.modelCode.trim()) {
      base.modelCode = query.modelCode.trim()
    }
    if (query.equipmentName.trim()) {
      base.equipmentName = query.equipmentName.trim()
    }
    if (query.categoryId) {
      base.categoryId = query.categoryId
    }
    const all = []
    let totalCount = Infinity
    let pageNum = 1
    while (all.length < totalCount) {
      const data = await pageModels({ ...base, pageNum })
      const batch = data.records || []
      all.push(...batch)
      totalCount = data.total || 0
      pageNum += 1
      if (!batch.length) {
        break
      }
    }
    records.value = all
    emit('count', all.length)
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

function search() {
  groupPageNum.value = 1
  load()
}

/* ---------------- 型号码 / 设备名称 typeahead ---------------- */

/**
 * 联想候选：按输入片段查前 10 条型号，取对应字段去重映射为 { value }。
 * 失败静默返回空（列表查询错误由拦截器统一提示，输入辅助不重复打扰）。
 */
async function fetchSuggestions(queryString, field) {
  const params = { pageNum: 1, pageSize: 10 }
  if (queryString && queryString.trim()) {
    params[field] = queryString.trim()
  }
  const data = await pageModels(params)
  const seen = new Set()
  const list = []
  for (const row of data.records || []) {
    const text = row[field]
    if (text && !seen.has(text)) {
      seen.add(text)
      list.push({ value: text })
    }
  }
  return list
}

function suggestModelCodes(queryString, cb) {
  fetchSuggestions(queryString, 'modelCode').then(cb).catch(() => cb([]))
}

function suggestEquipmentNames(queryString, cb) {
  fetchSuggestions(queryString, 'equipmentName').then(cb).catch(() => cb([]))
}

/* ---------------- 设备明细（型号下的每台设备） ---------------- */

const assetsDialog = reactive({
  visible: false,
  loading: false,
  modelId: '',
  modelText: '',
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: 10
})

function valueLevelMeta(row) {
  return row.valueLevel
    ? enumMeta(row.valueLevel, VALUE_LEVEL)
    : { label: '待分类', pill: 'pill--danger' }
}

function openAssets(row) {
  assetsDialog.modelId = row.id
  assetsDialog.modelText = `${row.modelCode} · ${row.equipmentName}`
  assetsDialog.pageNum = 1
  assetsDialog.visible = true
  loadAssets()
}

async function loadAssets() {
  assetsDialog.loading = true
  try {
    const data = await pageAssets({
      pageNum: assetsDialog.pageNum,
      pageSize: assetsDialog.pageSize,
      modelId: assetsDialog.modelId
    })
    assetsDialog.records = data.records || []
    assetsDialog.total = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    assetsDialog.loading = false
  }
}

/* ---------------- 分类抽屉 ---------------- */
const categoryDrawerVisible = ref(false)

/** 抽屉内分类变更后：刷新分类树（筛选/元数据）与型号分组 */
function onCategoryChanged() {
  loadCategoryTree()
  load()
}

/* ---------------- 弹窗 ---------------- */

const dialog = reactive({ visible: false, mode: 'create', saving: false })
const formRef = ref()
const form = reactive({
  id: '',
  categoryId: null,
  modelCode: '',
  equipmentName: '',
  brand: '',
  specification: '',
  quantity: 0,
  unitName: '',
  referencePrice: null,
  expectedLifeMonths: null,
  requiresDataErasure: false,
  enabled: 1
})
const rules = {
  categoryId: [{ required: true, message: '请选择所属分类', trigger: 'change' }],
  modelCode: [{ required: true, message: '请输入型号码', trigger: 'blur' }],
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  specification: [{ required: true, message: '请输入规格说明', trigger: 'blur' }],
  unitName: [{ required: true, message: '请输入计量单位', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'change' }]
}

/** group 存在时（分组头快捷入口）预选该分类 */
function openCreate(group) {
  dialog.mode = 'create'
  Object.assign(form, {
    id: '',
    categoryId: group ? group.categoryId : null,
    modelCode: '',
    equipmentName: '',
    brand: '',
    specification: '',
    unitName: '',
    referencePrice: null,
    expectedLifeMonths: null,
    requiresDataErasure: false,
    enabled: 1
  })
  dialog.visible = true
}

function openEdit(row) {
  dialog.mode = 'edit'
  Object.assign(form, {
    id: row.id,
    categoryId: row.categoryId,
    modelCode: row.modelCode,
    equipmentName: row.equipmentName,
    brand: row.brand || '',
    specification: row.specification || '',
    quantity: row.quantity ?? 0,
    unitName: row.unitName,
    referencePrice: row.referencePrice,
    expectedLifeMonths: row.expectedLifeMonths,
    requiresDataErasure: !!row.requiresDataErasure,
    enabled: row.enabled
  })
  dialog.visible = true
}

async function save() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  dialog.saving = true
  try {
    if (dialog.mode === 'create') {
      await createModel({
        categoryId: form.categoryId,
        modelCode: form.modelCode.trim(),
        equipmentName: form.equipmentName.trim(),
        brand: form.brand.trim() || null,
        specification: form.specification.trim(),
        quantity: form.quantity ?? 0,
        unitName: form.unitName.trim(),
        referencePrice: form.referencePrice ?? null,
        expectedLifeMonths: form.expectedLifeMonths ?? null,
        requiresDataErasure: form.requiresDataErasure
      })
    } else {
      await updateModel(form.id, {
        equipmentName: form.equipmentName.trim(),
        brand: form.brand.trim() || null,
        specification: form.specification.trim(),
        quantity: form.quantity ?? 0,
        unitName: form.unitName.trim(),
        referencePrice: form.referencePrice ?? null,
        expectedLifeMonths: form.expectedLifeMonths ?? null,
        requiresDataErasure: form.requiresDataErasure,
        enabled: form.enabled
      })
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    dialog.saving = false
  }
}

async function removeModel(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除型号「${row.equipmentName}（${row.modelCode}）」吗？删除后型号码不可复用。`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteModel(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

watch(
  () => props.active,
  (active) => {
    if (active && !loaded.value) {
      loaded.value = true
      load()
      loadCategoryTree()
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.model_manage__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.model_manage__filter {
  width: 200px;
}

/* 搜索框外套固定宽度容器：el-autocomplete 会把 class 透传到内部 el-input，
   scoped 选择器命不中，故宽度控制在 wrapper 上 */
.model_manage__search {
  width: 150px;
  flex: none;
}

/* 管理分类按钮靠右（提高特异性覆盖 .el-button + .el-button 相邻 margin） */
.model_manage__toolbar .model_manage__toolbar_category {
  margin-left: auto;
}

.model_manage__pager {
  margin-top: 14px;
  justify-content: flex-end;
}

/* 加载骨架：模拟分组行位（规范 7.8） */
.model_manage__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 2px 10px;
}

.model_manage__sk_row {
  height: 36px;
  border-radius: var(--r-sm);
}

.model_manage__sk_row--indent {
  width: 86%;
  margin-left: 38px;
}
</style>
