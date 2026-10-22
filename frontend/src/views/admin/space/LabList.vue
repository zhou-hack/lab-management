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
          <el-autocomplete
            v-model="query.labName"
            :fetch-suggestions="fetchKeywordSuggestions"
            :debounce="200"
            :trigger-on-focus="false"
            placeholder="输入名称或关键字，如 实训室"
            clearable
            class="space_list__input space_list__input--suggest"
            popper-class="space_suggest_popper"
            @select="onKeywordSelect"
            @keyup.enter="onSearch('enter')"
          >
            <template #default="{ item }">
              <div
                v-if="item.empty"
                class="space_list__suggest_empty"
              >
                {{ item.meta }}
              </div>
              <div
                v-else
                class="space_list__suggest"
              >
                <span class="space_list__suggest_name">{{ item.value }}</span>
                <span class="space_list__suggest_meta">{{ item.meta }}</span>
              </div>
            </template>
          </el-autocomplete>
        </el-form-item>
        <el-form-item label="位置">
          <el-input
            v-model="query.locationDetail"
            placeholder="如 A 楼 / 教学楼"
            clearable
            class="space_list__input"
            @keyup.enter="onSearch('enter')"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select
            v-model="query.labType"
            placeholder="全部"
            clearable
            class="space_list__select"
            @change="onFilterChange"
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
            @change="onFilterChange"
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
            @click="onSearch('button')"
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
        </div>
        <div class="space_list__toolbar_actions">
          <el-button
            v-permission="'space:space:create'"
            @click="importVisible = true"
          >
            导入
          </el-button>
          <el-button
            v-permission="'space:space:list'"
            :loading="exporting"
            @click="onExport"
          >
            导出 CSV
          </el-button>
          <el-button
            v-permission="'space:space:create'"
            type="primary"
            @click="onCreate"
          >
            新建场地
          </el-button>
        </div>
      </div>

      <!-- 批量操作条：选中行后出现 -->
      <div
        v-if="selectedRows.length"
        class="space_list__batch"
      >
        <span class="t-sub">已选 {{ selectedRows.length }} 项</span>
        <el-button
          v-permission="'space:space:update'"
          size="small"
          @click="onBatchStatus('AVAILABLE')"
        >
          批量启用
        </el-button>
        <el-button
          v-permission="'space:space:update'"
          size="small"
          type="warning"
          plain
          @click="onBatchStatus('CLOSED')"
        >
          批量停用
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        class="table-scroll-x"
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column
          type="selection"
          width="44"
        />
        <el-table-column
          label="场地名称"
          min-width="170"
        >
          <template #default="{ row }">
            <span class="space_list__name">{{ row.labName }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="类型"
          width="90"
        >
          <template #default="{ row }">
            {{ SPACE_TYPE_MAP[row.labType]?.label || row.labType }}
          </template>
        </el-table-column>
        <el-table-column
          prop="locationDetail"
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
          width="210"
          fixed="right"
          class-name="space_list__op_cell"
        >
          <template #default="{ row }">
            <div class="space_list__actions">
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
            </div>
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
      @preview="onPreviewSpace"
    />
    <SpaceDetailDrawer
      v-model="detailVisible"
      :space="detailSpace"
      @close="detailVisible = false"
    />
    <SpaceImportDialog
      v-model="importVisible"
      @close="importVisible = false"
      @success="onImported"
    />

    <!-- 页面左侧弹出式场地信息面板：点击面板外部或关闭按钮收起；容器不拦截页面与弹窗点击 -->
    <transition name="space_preview">
      <div
        v-if="previewVisible"
        class="space_preview"
      >
        <aside
          class="space_preview__panel"
          role="dialog"
          aria-label="场地信息"
        >
          <header class="space_preview__head">
            <div class="space_preview__head_text">
              <span class="t-sub">场地信息</span>
              <h3 class="space_preview__name">
                {{ previewDetail?.labName || previewName || '加载中…' }}
              </h3>
            </div>
            <div class="space_preview__head_actions">
              <span
                v-if="previewDetail"
                class="pill"
                :class="SPACE_STATUS_MAP[previewDetail.status]?.pill"
              >
                {{ SPACE_STATUS_MAP[previewDetail.status]?.label || previewDetail.status }}
              </span>
              <el-button
                link
                aria-label="关闭场地信息"
                @click="closePreview"
              >
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </header>

          <el-skeleton
            v-if="previewLoading"
            :rows="8"
            animated
          />
          <div
            v-else-if="previewError"
            class="space_preview__empty"
          >
            <p>{{ previewError }}</p>
            <el-button
              size="small"
              @click="retryPreview"
            >
              重试
            </el-button>
          </div>
          <dl
            v-else-if="previewDetail"
            class="space_preview__grid"
          >
            <div class="is_span2">
              <dt>场地编号</dt>
              <dd class="is_mono">
                {{ previewDetail.id }}
              </dd>
            </div>
            <div>
              <dt>场地类型</dt>
              <dd>{{ SPACE_TYPE_MAP[previewDetail.labType]?.label || previewDetail.labType }}</dd>
            </div>
            <div>
              <dt>容纳人数</dt>
              <dd>{{ previewDetail.capacity }} 人</dd>
            </div>
            <div>
              <dt>面积</dt>
              <dd>未登记</dd>
            </div>
            <div>
              <dt>使用状态</dt>
              <dd>{{ SPACE_STATUS_MAP[previewDetail.status]?.label || previewDetail.status }}</dd>
            </div>
            <div class="is_span2">
              <dt>位置</dt>
              <dd>{{ previewDetail.locationDetail || '—' }}</dd>
            </div>
            <div class="is_span2">
              <dt>开放时段</dt>
              <dd>{{ previewDetail.openStart }} - {{ previewDetail.openEnd }}</dd>
            </div>
            <div class="is_span2">
              <dt>设备配置</dt>
              <dd class="is_facilities">
                {{ previewDetail.facilities || '暂未登记' }}
              </dd>
            </div>
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatShanghai(previewDetail.createTime) }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatShanghai(previewDetail.updateTime) }}</dd>
            </div>
          </dl>
        </aside>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import { formatInstant } from '@/utils/datetime'
import SpaceFormDialog from './SpaceFormDialog.vue'
import SpaceDetailDrawer from './SpaceDetailDrawer.vue'
import SpaceImportDialog from './SpaceImportDialog.vue'
import {
  SPACE_TYPES,
  SPACE_TYPE_MAP,
  SPACE_STATUS,
  SPACE_STATUS_MAP,
  buildSpaceSuggestions
} from './space.constants'
import {
  pageSpaces,
  getSpaceStats,
  getSpaceDetail,
  listSpaceOptions,
  createSpace,
  updateSpace,
  enableSpace,
  disableSpace,
  deleteSpace,
  exportSpaces,
  batchStatusSpaces
} from '@/api/space'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const stats = reactive({ total: 0, available: 0, maintaining: 0, closed: 0 })
const selectedRows = ref([])
const exporting = ref(false)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  labName: '',
  locationDetail: '',
  labType: null,
  status: null,
  minCapacity: null,
  maxCapacity: null
})

const formVisible = ref(false)
const editingSpace = ref(null)
const detailVisible = ref(false)
const detailSpace = ref(null)
const importVisible = ref(false)

// ---------------------------------------------------------------------------
// 页面左侧弹出式场地信息面板：由新建/编辑弹窗选择场地名称后触发
// ---------------------------------------------------------------------------
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewError = ref('')
const previewDetail = ref(null)
const previewName = ref('')
const previewSpaceId = ref(null)
let previewSeq = 0

/** 耗时统计（毫秒），用于排查筛选与联想响应速度 */
function elapsedMs(begin) {
  return Math.round(performance.now() - begin)
}

async function loadStats() {
  const begin = performance.now()
  try {
    const data = await getSpaceStats()
    Object.assign(stats, {
      total: Number(data.total) || 0,
      available: Number(data.available) || 0,
      maintaining: Number(data.maintaining) || 0,
      closed: Number(data.closed) || 0
    })
    console.info('[场地统计] 加载完成', { ...stats, costMs: elapsedMs(begin) })
  } catch (e) {
    // 统计卡片失败不阻塞列表展示
    console.warn('[场地统计] 加载失败', { error: e?.message, costMs: elapsedMs(begin) })
  }
}

async function loadData() {
  const begin = performance.now()
  loading.value = true
  const params = {
    pageNum: query.pageNum,
    pageSize: query.pageSize,
    labName: query.labName || undefined,
    locationDetail: query.locationDetail || undefined,
    labType: query.labType || undefined,
    status: query.status || undefined,
    minCapacity: query.minCapacity || undefined,
    maxCapacity: query.maxCapacity || undefined
  }
  console.info('[场地筛选] 开始查询', params)
  try {
    const data = await pageSpaces(params)
    records.value = data.records || []
    total.value = Number(data.total) || 0
    console.info('[场地筛选] 查询完成', {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      labType: query.labType,
      status: query.status,
      labName: query.labName,
      pageRows: records.value.length,
      total: total.value,
      costMs: elapsedMs(begin)
    })
  } catch (e) {
    console.error('[场地筛选] 查询失败', { params, error: e?.message, costMs: elapsedMs(begin) })
    throw e
  } finally {
    loading.value = false
  }
}

function onSearch(trigger = 'manual') {
  console.info('[场地筛选] 触发查询', {
    trigger,
    labType: query.labType,
    status: query.status,
    labName: query.labName,
    locationDetail: query.locationDetail,
    minCapacity: query.minCapacity,
    maxCapacity: query.maxCapacity
  })
  query.pageNum = 1
  loadData()
}

/** 类型/状态筛选变更后立即按组合条件刷新列表与结果总数 */
function onFilterChange() {
  console.info('[场地筛选] 类型/状态变更，立即刷新', {
    labType: query.labType,
    status: query.status
  })
  onSearch('filter')
}

// 关键字实时联想：输入任意字符即请求场地选项，模糊匹配场地名称 + 标准词条
let keywordSuggestSeq = 0
let lastKeywordInput = ''

async function fetchKeywordSuggestions(queryString, cb) {
  const begin = performance.now()
  const keyword = String(queryString || '').trim()
  lastKeywordInput = keyword
  if (!keyword) {
    console.debug('[关键字联想] 输入为空，不发起联想')
    cb([])
    return
  }
  const seq = ++keywordSuggestSeq
  console.info('[关键字联想] 开始请求', { keyword, seq })
  try {
    const options = await listSpaceOptions({ keyword, limit: 50 })
    if (seq !== keywordSuggestSeq) {
      console.info('[关键字联想] 丢弃过期响应', { keyword, seq, latestSeq: keywordSuggestSeq })
      return
    }
    const list = buildSpaceSuggestions(keyword, options, { limit: 50 })
    console.info('[关键字联想] 请求完成', {
      keyword,
      optionCount: options?.length || 0,
      suggestionCount: list.length,
      empty: !list.length,
      costMs: elapsedMs(begin)
    })
    cb(list.length ? list : [{ value: '', empty: true, meta: '无匹配的场地名称或关键字' }])
  } catch (e) {
    console.warn('[关键字联想] 请求失败', { keyword, error: e?.message, costMs: elapsedMs(begin) })
    if (seq === keywordSuggestSeq) {
      cb([{ value: '', empty: true, meta: '联想加载失败，请稍后重试' }])
    }
  }
}

/** 点击候选：场地名称直接查询；空态项恢复原输入，避免被清空 */
function onKeywordSelect(item) {
  if (item?.empty) {
    console.info('[关键字联想] 点击空态项，恢复原输入', { keyword: lastKeywordInput })
    query.labName = lastKeywordInput
    return
  }
  console.info('[关键字联想] 选择候选，立即查询', { value: item?.value, kind: item?.kind })
  onSearch('suggest')
}

function onReset() {
  console.info('[场地筛选] 重置筛选条件', {
    before: {
      labName: query.labName,
      locationDetail: query.locationDetail,
      labType: query.labType,
      status: query.status,
      minCapacity: query.minCapacity,
      maxCapacity: query.maxCapacity
    }
  })
  query.labName = ''
  query.locationDetail = ''
  query.labType = null
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

/** 导入有成功行后刷新列表与统计卡片 */
function onImported() {
  query.pageNum = 1
  loadData()
  loadStats()
}

function onEdit(row) {
  editingSpace.value = row
  formVisible.value = true
}

async function onDetail(row) {
  try {
    detailSpace.value = await getSpaceDetail(row.id)
    detailVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '详情加载失败')
  }
}

/** 弹窗选中场地名称后：页面左侧弹出信息面板并加载详情 */
function onPreviewSpace(option) {
  if (!option?.id) return
  previewVisible.value = true
  previewName.value = option.labName || ''
  previewSpaceId.value = String(option.id)
  previewDetail.value = null
  previewError.value = ''
  console.info('[场地信息面板] 打开', {
    spaceId: option.id,
    labName: option.labName,
    activeElement: document.activeElement?.tagName || ''
  })
  bindPreviewGlobal()
  loadPreviewDetail(option.id)
}

async function loadPreviewDetail(id) {
  const begin = performance.now()
  const seq = ++previewSeq
  previewLoading.value = true
  previewError.value = ''
  console.info('[场地信息面板] 加载开始', { spaceId: id })
  try {
    const detail = await getSpaceDetail(id)
    if (seq !== previewSeq) {
      console.info('[场地信息面板] 丢弃过期响应', { spaceId: id, seq, latestSeq: previewSeq })
      return
    }
    previewDetail.value = detail
    previewName.value = detail.labName
    console.info('[场地信息面板] 加载完成', {
      spaceId: id,
      labName: detail.labName,
      costMs: elapsedMs(begin)
    })
  } catch (e) {
    if (seq !== previewSeq) return
    previewError.value = e?.message || '场地信息加载失败'
    console.warn('[场地信息面板] 加载失败', { spaceId: id, error: e?.message, costMs: elapsedMs(begin) })
  } finally {
    if (seq === previewSeq) previewLoading.value = false
  }
}

function closePreview() {
  if (!previewVisible.value) return
  previewVisible.value = false
  previewSeq += 1
  console.info('[场地信息面板] 关闭', { spaceId: previewSpaceId.value })
  unbindPreviewGlobal()
}

// 点击面板外部任意处收起（捕获阶段监听，不拦截弹窗等页面元素的正常交互）
let previewGlobalBound = false

function onGlobalPointerDown(event) {
  if (!previewVisible.value) return
  if (event.target?.closest?.('.space_preview__panel')) return
  closePreview()
}

function bindPreviewGlobal() {
  if (previewGlobalBound) return
  document.addEventListener('mousedown', onGlobalPointerDown, true)
  previewGlobalBound = true
}

function unbindPreviewGlobal() {
  if (!previewGlobalBound) return
  document.removeEventListener('mousedown', onGlobalPointerDown, true)
  previewGlobalBound = false
}

// 新建/编辑弹窗关闭（提交成功/取消）时联动收起左侧面板，避免残留旧场地信息
watch(formVisible, (visible) => {
  if (!visible) closePreview()
})

onBeforeUnmount(unbindPreviewGlobal)

function retryPreview() {
  if (previewSpaceId.value) loadPreviewDetail(previewSpaceId.value)
}

/** UTC ISO 时间点按上海时区展示（不依赖浏览器本地时区） */
function formatShanghai(value) {
  return value ? formatInstant(value) : '—'
}

async function onSubmitForm({ id, payload }) {
  await (id ? updateSpace(id, payload) : createSpace(payload))
  ElMessage.success(id ? '场地已更新' : '场地已创建')
  formVisible.value = false
  loadData()
  loadStats()
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
  await (enabled ? enableSpace(row.id) : disableSpace(row.id))
  ElMessage.success(enabled ? '已启用' : '已停用')
  loadData()
  loadStats()
}

function onSelectionChange(rows) {
  selectedRows.value = rows || []
}

/** 批量启停（target 仅 AVAILABLE/CLOSED；停用需二次确认） */
async function onBatchStatus(target) {
  const enabled = target === 'AVAILABLE'
  const names = selectedRows.value.map((r) => r.labName).join('、')
  try {
    await ElMessageBox.confirm(
      `确定批量${enabled ? '启用' : '停用'} ${selectedRows.value.length} 个场地（${names}）吗？${
        enabled ? '启用后可恢复预约。' : '停用后不可新建预约。'
      }`,
      '批量操作确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  const result = await batchStatusSpaces({
    ids: selectedRows.value.map((r) => r.id),
    target
  })
  const missing = result?.missing?.length || 0
  ElMessage.success(`批量${enabled ? '启用' : '停用'}完成：更新 ${result?.updated || 0} 项${missing ? `，${missing} 项不存在或已删除` : ''}`)
  loadData()
  loadStats()
}

/** 按当前筛选条件导出 CSV（UTF-8 BOM，时间按上海时区展示） */
async function onExport() {
  exporting.value = true
  try {
    const blob = await exportSpaces({
      labName: query.labName || undefined,
      locationDetail: query.locationDetail || undefined,
      labType: query.labType || undefined,
      status: query.status || undefined,
      minCapacity: query.minCapacity || undefined,
      maxCapacity: query.maxCapacity || undefined
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `spaces_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    exporting.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除场地「${row.labName}」吗？逻辑删除且名称不可复用；存在未结预约或数据范围授权时将被后端拒绝。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteSpace(row.id)
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

.space_list__input--suggest {
  width: 220px;
}

/* 关键字实时联想候选（下拉由 Element Plus teleport 到 body，层级高于页面元素） */
.space_list__suggest {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.space_list__suggest_name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--g1);
}

.space_list__suggest_meta {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--g4);
}

.space_list__suggest_empty {
  padding: 4px 0;
  font-size: 12px;
  color: var(--g3);
  text-align: center;
}

/* 页面左侧弹出式场地信息面板（层级高于弹窗；容器不拦截点击，由全局监听实现点击外部收起） */
.space_preview {
  position: fixed;
  inset: 0;
  z-index: 3200;
  pointer-events: none;
}

.space_preview__panel {
  position: absolute;
  left: 16px;
  top: 84px;
  width: clamp(280px, 26vw, 360px);
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: var(--surface);
  border: .5px solid var(--hair);
  border-radius: var(--r-md);
  box-shadow: 0 18px 48px rgba(15, 23, 42, .18);
  pointer-events: auto;
}

.space_preview__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.space_preview__name {
  margin: 2px 0 0;
  font-size: 16px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
  word-break: break-all;
}

.space_preview__head_actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.space_preview__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  margin: 0;
}

.space_preview__grid > div {
  min-width: 0;
}

.space_preview__grid dt {
  margin-bottom: 3px;
  font-size: 12px;
  color: var(--g3);
}

.space_preview__grid dd {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--g1);
  word-break: break-word;
}

.space_preview__grid .is_span2 {
  grid-column: 1 / -1;
}

.space_preview__grid .is_mono {
  font-family: var(--font-num);
  font-size: 12px;
  color: var(--g2);
  word-break: break-all;
}

.space_preview__grid .is_facilities {
  white-space: pre-wrap;
}

.space_preview__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 8px;
  font-size: 13px;
  color: var(--g3);
  text-align: center;
}

.space_preview__empty p {
  margin: 0;
}

/* 平滑展开/收起（240ms） */
.space_preview-enter-active,
.space_preview-leave-active {
  transition: opacity var(--dur) var(--ease);
}

.space_preview-enter-from,
.space_preview-leave-to {
  opacity: 0;
}

.space_preview-enter-active .space_preview__panel,
.space_preview-leave-active .space_preview__panel {
  transition: transform var(--dur) var(--ease);
}

.space_preview-enter-from .space_preview__panel,
.space_preview-leave-to .space_preview__panel {
  transform: translateX(-24px);
}

@media (prefers-reduced-motion: reduce) {
  .space_preview-enter-active,
  .space_preview-leave-active,
  .space_preview-enter-active .space_preview__panel,
  .space_preview-leave-active .space_preview__panel {
    transition: none;
  }
}

@media (max-width: 768px) {
  .space_preview__panel {
    left: 12px;
    right: 12px;
    top: 64px;
    width: auto;
  }
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

.space_list__toolbar_actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.space_list__batch {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 12px;
  background: var(--surface-sunken, #f5f7fa);
  border-radius: 8px;
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

/* 操作列：五个按钮强制单行，禁止换行 */
.space_list__actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 2px;
  white-space: nowrap;
}

/* 覆盖 Element Plus 相邻按钮默认 12px 左外边距，改用 gap 统一微调 */
.space_list__actions .el-button + .el-button {
  margin-left: 0;
}

/* link 按钮左右内边距收紧，保证操作按钮在 210px 列宽内单行排布 */
.space_list__actions .el-button.is-link {
  padding: 2px 3px;
}

:deep(.space_list__op_cell .cell) {
  white-space: nowrap;
}

/* 场地名称：仅作文本展示，不绑定点击交互 */
.space_list__name {
  color: var(--g1);
}

@media (max-width: 768px) {
  .space_list__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .space_list__toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .space_list__toolbar_actions {
    justify-content: flex-end;
  }

  .space_list__input,
  .space_list__select {
    width: 100%;
  }
}
</style>
