<template>
  <div>
    <div class="toolbar">
      <LocationSelect
        v-model="query.locationId"
        placeholder="全部库位"
        all-option
        @change="pager.search"
      />
      <SkuSelect
        v-model="query.skuId"
        all-option
        @change="pager.search"
      />
      <el-select
        v-model="query.direction"
        clearable
        placeholder="全部方向"
        class="w150"
        @change="pager.search"
      >
        <el-option
          label="全部方向"
          value=""
        />
        <el-option
          label="入库 IN"
          value="IN"
        />
        <el-option
          label="出库 OUT"
          value="OUT"
        />
      </el-select>
      <el-date-picker
        v-model="query.timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DD HH:mm:ss"
        start-placeholder="出入库时间起"
        end-placeholder="出入库时间止"
        class="w280"
        @change="pager.search"
      />
      <el-button
        v-permission="['item:stock:create', 'item:stock:post']"
        type="primary"
        @click="openCreate"
      >
        新建流水
      </el-button>
      <el-button @click="pager.search">
        查询
      </el-button>
    </div>

    <el-table
      v-loading="pager.loading"
      :data="pager.rows"
      border
    >
      <el-table-column
        label="出入库时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.occurredAt) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="locationName"
        label="库位"
        min-width="170"
        show-overflow-tooltip
      />
      <el-table-column
        label="物料"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <div>{{ row.skuName }}</div>
          <div class="muted small">
            {{ row.skuCode }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="方向"
        width="80"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.direction"
            :map="moveDirectionMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="入库数量"
        width="110"
        align="right"
      >
        <template #default="{ row }">
          <span v-if="row.direction === 'IN'">{{ formatQty(row.qtyDelta) }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column
        label="出库数量"
        width="110"
        align="right"
      >
        <template #default="{ row }">
          <span v-if="row.direction === 'OUT'">{{ formatQty(Math.abs(row.qtyDelta)) }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column
        label="总数量"
        width="110"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.totalQty) }}
        </template>
      </el-table-column>
      <el-table-column
        label="入库人"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          {{ row.direction === 'IN' ? displayName(row.operatorName, row.operatorId) : '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="出库人"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          {{ row.direction === 'OUT' ? displayName(row.operatorName, row.operatorId) : '—' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="stockDocumentId"
        label="单据 ID"
        width="180"
        show-overflow-tooltip
      />
    </el-table>

    <PagerBar :list="pager" />

    <!-- 新建流水（差值生成 ADJUST 调账单并自动审批过账，立即生效） -->
    <el-dialog
      v-model="createVisible"
      title="新建库存流水"
      width="520px"
    >
      <el-form label-width="100px">
        <el-form-item
          label="出入库时间"
          required
        >
          <el-date-picker
            v-model="createForm.occurredAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择出入库时间"
            class="w240"
          />
        </el-form-item>
        <el-form-item
          label="库位"
          required
        >
          <LocationSelect
            v-model="createForm.locationId"
            class="w240"
          />
        </el-form-item>
        <el-form-item
          label="物料"
          required
        >
          <SkuSelect
            v-model="createForm.skuId"
            class="w240"
          />
        </el-form-item>
        <el-form-item
          label="方向"
          required
        >
          <el-radio-group v-model="createForm.direction">
            <el-radio-button value="IN">
              入库
            </el-radio-button>
            <el-radio-button value="OUT">
              出库
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="变更前数量">
          <span v-if="beforeQty !== null">
            {{ formatQty(beforeQty) }}{{ beforeUnit ? ' ' + beforeUnit : '' }}
          </span>
          <span
            v-else
            class="muted"
          >选择库位与物料后自动带出</span>
        </el-form-item>
        <el-form-item
          :label="createForm.direction === 'IN' ? '入库数量' : '出库数量'"
          required
        >
          <el-input-number
            v-model="createForm.targetQty"
            :min="0"
            :step="1"
            :precision="3"
            controls-position="right"
          />
          <span class="muted small ml4">目标数量（非增减量）</span>
        </el-form-item>
        <el-form-item
          :label="createForm.direction === 'OUT' ? '出库人' : '入库人'"
          required
        >
          <el-autocomplete
            v-model="createForm.operatorName"
            :fetch-suggestions="queryOperatorOptions"
            placeholder="请输入姓名"
            clearable
            style="width: 100%"
          >
            <template #default="{ item }">
              <div class="operator-option">
                <span>{{ item.value }}</span>
                <span
                  v-if="item.tail"
                  class="muted small"
                >{{ item.tail }}</span>
              </div>
            </template>
          </el-autocomplete>
        </el-form-item>
        <el-form-item
          label="事由"
          required
        >
          <el-input
            v-model="createForm.reason"
            type="textarea"
            :rows="2"
            maxlength="2000"
            placeholder="如补录历史出入库（必填，记入调账单审计）"
          />
        </el-form-item>
      </el-form>
      <div class="muted small">
        差值自动生成 ADJUST 调账单并审批过账，余额立即更新，本列表即时可见。
      </div>
      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCreate"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createMovement, custodianOptions, pageBalances, pageMovements } from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { moveDirectionMap } from '@/utils/statusMap'
import { fmtShanghai, formatQty, shanghaiToUtcIso } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import LocationSelect from '@/components/LocationSelect.vue'
import SkuSelect from '@/components/SkuSelect.vue'

const query = reactive({ locationId: null, skuId: null, direction: null, timeRange: null })

const pager = usePagedList((params) => {
  const range = query.timeRange || []
  return pageMovements({
    ...params,
    locationId: query.locationId || undefined,
    skuId: query.skuId || undefined,
    direction: query.direction || undefined,
    occurredFrom: range[0] ? shanghaiToUtcIso(range[0]) : undefined,
    occurredTo: range[1] ? shanghaiToUtcIso(range[1]) : undefined
  })
})

/** 姓名/ID 均缺失（经办人未登记）时显示占位 */
function displayName(name, id) {
  if (name) return name
  return id || '—'
}

// ---- 新建流水（差值生成 ADJUST 调账单自动审批过账，立即生效） ----
const createVisible = ref(false)
const submitting = ref(false)
const beforeQty = ref(null)
const beforeUnit = ref('')
const createForm = reactive({
  occurredAt: '',
  locationId: '',
  skuId: '',
  direction: 'IN',
  targetQty: null,
  operatorName: '',
  reason: ''
})

/** 入库人/出库人候选：按姓名/账号/学工号远程搜索启用用户，也可自由输入（跨模块只读） */
async function queryOperatorOptions(keyword, cb) {
  try {
    const users = await custodianOptions(keyword || undefined)
    cb(users.map((u) => ({ value: u.realName, tail: u.personNo || u.accountName || '' })))
  } catch {
    cb([])
  }
}

let beforeSeq = 0
/** 选择库位+物料后自动查询当前库存作为「变更前」；无余额行按 0 处理 */
async function loadBefore() {
  const seq = ++beforeSeq
  beforeQty.value = null
  beforeUnit.value = ''
  if (!createForm.locationId || !createForm.skuId) return
  try {
    const data = await pageBalances({
      pageNum: 1,
      pageSize: 10,
      locationId: createForm.locationId,
      skuId: createForm.skuId
    })
    if (seq !== beforeSeq) return
    const row = data.records?.[0]
    beforeQty.value = row ? Number(row.stockQty) : 0
    beforeUnit.value = row?.baseUnit || ''
  } catch {
    if (seq === beforeSeq) beforeQty.value = null
  }
}

watch(() => [createForm.locationId, createForm.skuId], loadBefore)

function openCreate() {
  createForm.occurredAt = fmtShanghai(new Date().toISOString())
  createForm.locationId = ''
  createForm.skuId = ''
  createForm.direction = 'IN'
  createForm.targetQty = null
  createForm.operatorName = ''
  createForm.reason = ''
  beforeQty.value = null
  beforeUnit.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.occurredAt || !createForm.locationId || !createForm.skuId
    || createForm.targetQty === null || !createForm.operatorName.trim()
    || !createForm.reason.trim()) {
    ElMessage.warning('请填写出入库时间、库位、物料、变更后数量、入库人/出库人与事由')
    return
  }
  if (beforeQty.value !== null && createForm.targetQty === beforeQty.value) {
    ElMessage.warning('变更后数量与当前库存一致，无需登记')
    return
  }
  if (beforeQty.value !== null) {
    if (createForm.direction === 'IN' && createForm.targetQty < beforeQty.value) {
      ElMessage.warning('入库方向要求变更后数量不小于变更前')
      return
    }
    if (createForm.direction === 'OUT' && createForm.targetQty > beforeQty.value) {
      ElMessage.warning('出库方向要求变更后数量不大于变更前')
      return
    }
  }
  submitting.value = true
  try {
    await createMovement({
      locationId: createForm.locationId,
      skuId: createForm.skuId,
      direction: createForm.direction,
      targetQty: createForm.targetQty,
      occurredAt: shanghaiToUtcIso(createForm.occurredAt),
      operatorName: createForm.operatorName.trim(),
      reason: createForm.reason.trim()
    })
    ElMessage.success('流水已登记，物料存量已同步更新')
    createVisible.value = false
    pager.search()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  pager.load()
})

defineExpose({ refresh: pager.load })
</script>

<style scoped>
/* 单行工具栏压缩：新增「新建流水」按钮后收紧方向下拉（150px）与时间范围（280px），
   全部控件选中长文本时总宽约 1370px，不超过工作台内容区宽（1392px），保证不换行不滚动 */
.toolbar :deep(.el-select.w150) {
  width: 150px;
  min-width: 150px;
  max-width: 150px;
}

/* EP 对 datetimerange 有主题默认宽（约 400px），普通单类选择器不稳；
   用 .toolbar + 组件类 + 自定义类三重抬高特异性压到 280px */
.toolbar :deep(.el-date-editor--datetimerange.w280) {
  width: 280px;
}

/* 入库人/出库人候选行：姓名 + 账号/学工号辅助信息两端对齐 */
.operator-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
</style>
