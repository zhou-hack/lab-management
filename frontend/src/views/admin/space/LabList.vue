<template>
  <div class="space_list">
    <!-- 统计卡片区 -->
    <div class="space_list__stats">
      <div class="card space_list__stat">
        <p class="space_list__stat_label">
          场地总数
        </p>
        <p class="space_list__stat_num space_list__stat_num--brand">
          {{ stats.total }}
        </p>
      </div>
      <div class="card space_list__stat">
        <p class="space_list__stat_label">
          可用
        </p>
        <p class="space_list__stat_num space_list__stat_num--ok">
          {{ stats.available }}
        </p>
      </div>
      <div class="card space_list__stat">
        <p class="space_list__stat_label">
          维护中
        </p>
        <p class="space_list__stat_num space_list__stat_num--warn">
          {{ stats.maintaining }}
        </p>
      </div>
      <div class="card space_list__stat">
        <p class="space_list__stat_label">
          停用
        </p>
        <p class="space_list__stat_num space_list__stat_num--muted">
          {{ stats.closed }}
        </p>
      </div>
    </div>

    <!-- 筛选区 -->
    <div class="card space_list__filters">
      <el-form
        :inline="true"
        :model="query"
        class="space_list__form"
      >
        <el-form-item label="关键字">
          <el-input
            v-model="query.spaceName"
            placeholder="场地名称模糊匹配"
            clearable
            class="space_list__input"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="位置">
          <el-input
            v-model="query.location"
            placeholder="如 A 楼 / 教学楼"
            clearable
            class="space_list__input"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select
            v-model="query.spaceType"
            placeholder="全部"
            clearable
            class="space_list__select"
          >
            <el-option
              v-for="t in SPACE_TYPES"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            class="space_list__select"
          >
            <el-option
              v-for="s in SPACE_STATUS"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="容量">
          <div class="space_list__range">
            <el-input-number
              v-model="query.minCapacity"
              :min="1"
              :max="9999"
              :controls="false"
              placeholder="≥"
              class="space_list__num"
            />
            <span class="space_list__dash">—</span>
            <el-input-number
              v-model="query.maxCapacity"
              :min="1"
              :max="9999"
              :controls="false"
              placeholder="≤"
              class="space_list__num"
            />
          </div>
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
    <div class="card space_list__table_card">
      <div class="space_list__toolbar">
        <div>
          <h2 class="t-title space_list__title">
            场地档案
          </h2>
          <p class="t-sub">
            教室/实训室建档与状态维护 · 开放时段为上海本地时间（不做时区转换）
          </p>
        </div>
        <el-button
          v-permission="'space:space:create'"
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
          label="场地名称"
          min-width="170"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="onDetail(row)"
            >
              {{ row.spaceName }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column
          label="类型"
          width="90"
        >
          <template #default="{ row }">
            {{ SPACE_TYPE_MAP[row.spaceType]?.label || row.spaceType }}
          </template>
        </el-table-column>
        <el-table-column
          prop="location"
          label="位置"
          min-width="130"
        />
        <el-table-column
          prop="capacity"
          label="容量"
          width="70"
          align="right"
        />
        <el-table-column
          label="开放时段"
          width="110"
        >
          <template #default="{ row }">
            {{ row.openStart }} - {{ row.openEnd }}
          </template>
        </el-table-column>
        <el-table-column
          prop="facilities"
          label="设备清单"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.facilities || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="88"
        >
          <template #default="{ row }">
            <span
              class="pill"
              :class="SPACE_STATUS_MAP[row.status]?.pill"
            >
              {{ SPACE_STATUS_MAP[row.status]?.label || row.status }}
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
              v-permission="'space:space:update'"
              link
              type="primary"
              @click="onEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status !== 'CLOSED'"
              v-permission="'space:space:update'"
              link
              type="warning"
              @click="onToggle(row, false)"
            >
              停用
            </el-button>
            <el-button
              v-else
              v-permission="'space:space:update'"
              link
              type="success"
              @click="onToggle(row, true)"
            >
              启用
            </el-button>
            <el-button
              v-permission="'space:space:delete'"
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

      <div class="space_list__pager">
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

    <SpaceFormDialog
      v-model="formVisible"
      :space="editingSpace"
      @close="formVisible = false"
      @submit="onSubmitForm"
    />
    <SpaceDetailDrawer
      v-model="detailVisible"
      :space="detailSpace"
      @close="detailVisible = false"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SpaceFormDialog from './SpaceFormDialog.vue'
import SpaceDetailDrawer from './SpaceDetailDrawer.vue'
import { SPACE_TYPES, SPACE_TYPE_MAP, SPACE_STATUS, SPACE_STATUS_MAP } from './space.constants'
import {
  fetchSpacePage,
  fetchSpaceStats,
  fetchSpaceDetail,
  saveSpace,
  switchSpaceEnabled,
  removeSpace
} from './space.service'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const stats = reactive({ total: 0, available: 0, maintaining: 0, closed: 0 })

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  spaceName: '',
  location: '',
  spaceType: null,
  status: null,
  minCapacity: null,
  maxCapacity: null
})

const formVisible = ref(false)
const editingSpace = ref(null)
const detailVisible = ref(false)
const detailSpace = ref(null)

async function loadStats() {
  try {
    const data = await fetchSpaceStats()
    Object.assign(stats, {
      total: Number(data.total) || 0,
      available: Number(data.available) || 0,
      maintaining: Number(data.maintaining) || 0,
      closed: Number(data.closed) || 0
    })
  } catch {
    // 统计卡片失败不阻塞列表展示
  }
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      spaceName: query.spaceName || undefined,
      location: query.location || undefined,
      spaceType: query.spaceType || undefined,
      status: query.status || undefined,
      minCapacity: query.minCapacity || undefined,
      maxCapacity: query.maxCapacity || undefined
    }
    const data = await fetchSpacePage(params)
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
  query.spaceName = ''
  query.location = ''
  query.spaceType = null
  query.status = null
  query.minCapacity = null
  query.maxCapacity = null
  query.pageNum = 1
  loadData()
}

function onCreate() {
  editingSpace.value = null
  formVisible.value = true
}

function onEdit(row) {
  editingSpace.value = row
  formVisible.value = true
}

async function onDetail(row) {
  try {
    detailSpace.value = await fetchSpaceDetail(row.id)
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '详情加载失败')
  }
}

async function onSubmitForm({ id, payload }) {
  await saveSpace(id, payload)
  ElMessage.success(id ? '场地已更新' : '场地已创建')
  formVisible.value = false
  loadData()
  loadStats()
}

async function onToggle(row, enabled) {
  try {
    await ElMessageBox.confirm(
      `确定${enabled ? '启用' : '停用'}场地「${row.spaceName}」吗？${
        enabled ? '启用后可恢复预约与排课。' : '停用后不可新建预约。'
      }`,
      '操作确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await switchSpaceEnabled(row.id, enabled)
  ElMessage.success(enabled ? '已启用' : '已停用')
  loadData()
  loadStats()
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除场地「${row.spaceName}」吗？逻辑删除且名称不可复用；存在数据范围授权时将被后端拒绝。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await removeSpace(row.id)
  ElMessage.success('已删除')
  if (records.value.length === 1 && query.pageNum > 1) {
    query.pageNum -= 1
  }
  loadData()
  loadStats()
}

onMounted(() => {
  loadData()
  loadStats()
})
</script>

<style scoped>
.space_list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.space_list__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.space_list__stat {
  padding: 16px 20px;
}

.space_list__stat_label {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--g3, #6b7280);
}

.space_list__stat_num {
  margin: 0;
  font-size: 28px;
  font-weight: var(--fw-semibold, 600);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.space_list__stat_num--brand {
  color: var(--brand, #3f86ff);
}

.space_list__stat_num--ok {
  color: var(--ok, #2e9e5b);
}

.space_list__stat_num--warn {
  color: var(--warn, #d97706);
}

.space_list__stat_num--muted {
  color: var(--g3, #6b7280);
}

.space_list__form {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}

.space_list__input {
  width: 160px;
}

.space_list__select {
  width: 120px;
}

.space_list__range {
  display: flex;
  align-items: center;
  gap: 6px;
}

.space_list__num {
  width: 64px;
}

.space_list__dash {
  color: var(--g3, #6b7280);
}

.space_list__table_card {
  padding: 20px;
}

.space_list__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.space_list__title {
  margin: 0 0 4px;
  font-size: 18px;
}

.space_list__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .space_list__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .space_list__toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .space_list__input,
  .space_list__select {
    width: 100%;
  }
}
</style>
