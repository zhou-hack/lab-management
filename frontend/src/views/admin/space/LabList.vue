<template>
  <div class="lab_list">
    <!-- 筛选区 -->
    <div class="card lab_list__filters">
      <el-form
        :inline="true"
        :model="query"
        class="lab_list__form"
      >
        <el-form-item label="关键字">
          <el-input
            v-model="query.labName"
            placeholder="场地名称"
            clearable
            class="lab_list__input"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="编码">
          <el-input
            v-model="query.labCode"
            placeholder="精确匹配"
            clearable
            class="lab_list__input"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="校区">
          <el-select
            v-model="query.campusId"
            placeholder="全部"
            clearable
            class="lab_list__select"
            @change="onCampusFilterChange"
          >
            <el-option
              v-for="c in campusOptions"
              :key="c.id"
              :label="c.label"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="楼栋">
          <el-select
            v-model="query.buildingId"
            placeholder="全部"
            clearable
            class="lab_list__select"
          >
            <el-option
              v-for="b in filteredBuildingOptions"
              :key="b.id"
              :label="b.label"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select
            v-model="query.labType"
            placeholder="全部"
            clearable
            class="lab_list__select"
          >
            <el-option
              v-for="t in LAB_TYPES"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.enabled"
            placeholder="全部"
            clearable
            class="lab_list__select_sm"
          >
            <el-option
              label="启用"
              :value="1"
            />
            <el-option
              label="停用"
              :value="0"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            @click="onSearch"
          >
            查询
          </el-button>
          <el-button @click="onReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="card lab_list__table_card">
      <div class="lab_list__toolbar">
        <div>
          <h2 class="t-title lab_list__title">
            场地档案
          </h2>
          <p class="t-sub">
            实验室/教室建档与启停维护 · 状态为实时派生（不持久化）
          </p>
        </div>
        <el-button
          v-permission="'space:lab:create'"
          type="primary"
          @click="onCreate"
        >
          新建场地
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        class="table-scroll-x"
        row-key="id"
      >
        <el-table-column
          prop="labCode"
          label="编码"
          width="100"
        />
        <el-table-column
          label="场地名称"
          min-width="150"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="onDetail(row)"
            >
              {{ row.labName }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column
          label="类型"
          width="130"
        >
          <template #default="{ row }">
            {{ LAB_TYPE_MAP[row.labType]?.label || row.labType }}
          </template>
        </el-table-column>
        <el-table-column
          label="校区 / 楼栋"
          min-width="130"
        >
          <template #default="{ row }">
            {{ row.campusName }} / {{ row.buildingName }}
          </template>
        </el-table-column>
        <el-table-column
          label="位置"
          width="110"
        >
          <template #default="{ row }">
            {{ row.floorNo }}-{{ row.roomNo }}
          </template>
        </el-table-column>
        <el-table-column
          prop="capacity"
          label="容量"
          width="70"
          align="right"
        />
        <el-table-column
          label="面积(㎡)"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ row.areaSqm ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="92"
        >
          <template #default="{ row }">
            <span
              class="pill"
              :class="LAB_STATUS_MAP[row.status]?.pill"
            >
              {{ LAB_STATUS_MAP[row.status]?.label || row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="200"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="onDetail(row)"
            >
              详情
            </el-button>
            <el-button
              v-permission="'space:lab:update'"
              link
              type="primary"
              @click="onEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.enabled === 1"
              v-permission="'space:lab:update'"
              link
              type="warning"
              @click="onToggle(row, false)"
            >
              停用
            </el-button>
            <el-button
              v-else
              v-permission="'space:lab:update'"
              link
              type="success"
              @click="onToggle(row, true)"
            >
              启用
            </el-button>
            <el-button
              v-permission="'space:lab:delete'"
              link
              type="danger"
              @click="onDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="t-hint">暂无场地数据，点击右上角「新建场地」建档</span>
        </template>
      </el-table>

      <div class="lab_list__pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <LabFormDialog
      v-model="formVisible"
      :lab="editingLab"
      :campus-options="campusOptions"
      :building-options="formBuildingOptions"
      :department-options="departmentOptions"
      @close="formVisible = false"
      @submit="onSubmitForm"
    />
    <LabDetailDrawer
      v-model="detailVisible"
      :lab="detailLab"
      @close="detailVisible = false"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import LabFormDialog from './LabFormDialog.vue'
import LabDetailDrawer from './LabDetailDrawer.vue'
import { LAB_TYPES, LAB_TYPE_MAP, LAB_STATUS_MAP } from './space.constants'
import {
  fetchCampusOptions,
  fetchBuildingOptions,
  fetchDepartmentOptions,
  fetchLabPage,
  fetchLabDetail,
  saveLab,
  switchLabEnabled,
  removeLab
} from './space.service'

const loading = ref(false)
const records = ref([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  labName: '',
  labCode: '',
  campusId: null,
  buildingId: null,
  labType: null,
  enabled: null
})

const campusOptions = ref([])
const allBuildingOptions = ref([])
const departmentOptions = ref([])

const formVisible = ref(false)
const editingLab = ref(null)
const detailVisible = ref(false)
const detailLab = ref(null)

const filteredBuildingOptions = computed(() =>
  query.campusId
    ? allBuildingOptions.value.filter((b) => b.parentId === String(query.campusId))
    : allBuildingOptions.value
)

const formBuildingOptions = computed(() =>
  editingLab.value?.campusId
    ? allBuildingOptions.value.filter((b) => b.parentId === String(editingLab.value.campusId))
    : allBuildingOptions.value
)

async function loadOptions() {
  const [campuses, buildings, departments] = await Promise.all([
    fetchCampusOptions(),
    fetchBuildingOptions(null),
    fetchDepartmentOptions()
  ])
  campusOptions.value = campuses || []
  allBuildingOptions.value = buildings || []
  departmentOptions.value = (departments || []).map((d) => ({ id: String(d.id), name: d.name }))
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      labName: query.labName || undefined,
      labCode: query.labCode || undefined,
      campusId: query.campusId || undefined,
      buildingId: query.buildingId || undefined,
      labType: query.labType || undefined,
      enabled: query.enabled === null ? undefined : query.enabled
    }
    const data = await fetchLabPage(params)
    records.value = data.records || []
    total.value = Number(data.total) || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.labName = ''
  query.labCode = ''
  query.campusId = null
  query.buildingId = null
  query.labType = null
  query.enabled = null
  query.pageNum = 1
  loadData()
}

function onCampusFilterChange() {
  query.buildingId = null
}

function onCreate() {
  editingLab.value = null
  formVisible.value = true
}

function onEdit(row) {
  editingLab.value = row
  formVisible.value = true
}

async function onDetail(row) {
  try {
    detailLab.value = await fetchLabDetail(row.id)
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '详情加载失败')
  }
}

async function onSubmitForm({ id, payload }) {
  await saveLab(id, payload)
  ElMessage.success(id ? '场地已更新' : '场地已创建')
  formVisible.value = false
  loadData()
}

async function onToggle(row, enabled) {
  try {
    await ElMessageBox.confirm(
      `确定${enabled ? '启用' : '停用'}场地「${row.labName}」吗？${
        enabled ? '启用后可恢复预约与排课。' : '停用后不可新建预约。'
      }`,
      '操作确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await switchLabEnabled(row.id, enabled)
  ElMessage.success(enabled ? '已启用' : '已停用')
  loadData()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除场地「${row.labName}」吗？存在开放规则或数据范围授权时将被后端拒绝。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await removeLab(row.id)
  ElMessage.success('已删除')
  if (records.value.length === 1 && query.pageNum > 1) {
    query.pageNum -= 1
  }
  loadData()
}

onMounted(async () => {
  await loadOptions()
  await loadData()
})
</script>

<style scoped>
.lab_list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.lab_list__form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.lab_list__input {
  width: 150px;
}

.lab_list__select {
  width: 140px;
}

.lab_list__select_sm {
  width: 100px;
}

.lab_list__table_card {
  padding: 20px;
}

.lab_list__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.lab_list__title {
  margin: 0 0 4px;
  font-size: 18px;
}

.lab_list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .lab_list__toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .lab_list__input,
  .lab_list__select,
  .lab_list__select_sm {
    width: 100%;
  }
}
</style>
