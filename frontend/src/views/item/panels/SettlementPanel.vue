<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="query.issueLineId"
        clearable
        class="w240"
        placeholder="按原出库行 ID 过滤"
        @change="pager.search"
      />
      <el-button
        v-permission="'item:settlement:create'"
        type="primary"
        @click="openCreate"
      >
        核销登记
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
        prop="issueLineId"
        label="原出库行 ID"
        width="190"
        show-overflow-tooltip
      />
      <el-table-column
        label="物料"
        min-width="170"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <template v-if="row.skuCode">
            <div>{{ row.skuCode }}</div>
            <div class="muted small">
              {{ row.materialName }}
            </div>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="locationName"
        label="库位"
        width="150"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.locationName || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="类型"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.settlementType"
            :map="settlementTypeMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="数量"
        width="100"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.quantity) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="reason"
        label="原因"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="出库时间"
        width="170"
      >
        <template #default="{ row }">
          {{ row.occurredAt ? fmtShanghai(row.occurredAt) : '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="结算时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.settledAt) }}
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 核销登记 -->
    <el-dialog
      v-model="createVisible"
      title="核销登记（消耗 / 丢失 / 损坏）"
      width="680px"
    >
      <el-form label-width="110px">
        <el-form-item
          label="出库记录"
          required
        >
          <el-select
            v-model="form.issueLineId"
            v-loading="optionsLoading"
            filterable
            remote
            clearable
            :remote-method="searchIssueOptions"
            placeholder="搜索课程名称或物料编码/名称，选择要核销的出库记录"
            class="issue-option-select"
            @change="onIssueChange"
          >
            <el-option
              v-for="opt in issueOptions"
              :key="opt.issueLineId"
              :label="issueOptionLabel(opt)"
              :value="opt.issueLineId"
              :disabled="Number(opt.remainQty) <= 0"
            />
          </el-select>
        </el-form-item>
        <el-descriptions
          v-if="selectedIssue"
          :column="2"
          border
          size="small"
          class="issue-brief"
        >
          <el-descriptions-item label="课程名称">
            {{ selectedIssue.courseName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="库位">
            {{ selectedIssue.locationName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="物料">
            {{ selectedIssue.skuCode }} · {{ selectedIssue.materialName }}
          </el-descriptions-item>
          <el-descriptions-item label="出库人">
            {{ selectedIssue.operatorName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="出库时间">
            {{ fmtShanghai(selectedIssue.occurredAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="关联流水 ID">
            {{ selectedIssue.movementId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="原出库量">
            {{ formatQty(selectedIssue.quantity) }} {{ selectedIssue.baseUnit }}
          </el-descriptions-item>
          <el-descriptions-item label="已结算">
            {{ formatQty(selectedIssue.settledQty) }} {{ selectedIssue.baseUnit }}
          </el-descriptions-item>
          <el-descriptions-item
            label="申领用途"
            :span="2"
          >
            {{ selectedIssue.requestPurpose || '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-form-item
          label="核销类型"
          required
        >
          <el-select
            v-model="form.settlementType"
            class="w240"
          >
            <el-option
              v-for="(cfg, value) in createTypes"
              :key="value"
              :label="cfg.label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="数量"
          required
        >
          <el-input-number
            v-model="form.quantity"
            :min="0"
            :max="maxSettleQty"
            :step="1"
            :precision="3"
            controls-position="right"
          />
          <span
            v-if="selectedIssue"
            class="muted small remain-hint"
          >剩余可核销 {{ formatQty(selectedIssue.remainQty) }} {{ selectedIssue.baseUnit }}</span>
        </el-form-item>
        <el-form-item label="原因">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="2"
            placeholder="数量差异需说明原因"
          />
        </el-form-item>
      </el-form>
      <div class="muted small">
        累计核销量不能超过原出库量；归还入库过账时会自动生成归还结算记录。
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
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createSettlement, pageIssueOptions, pageSettlements } from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { settlementTypeMap } from '@/utils/statusMap'
import { fmtShanghai, formatQty } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'

const query = reactive({ issueLineId: '' })

/** 手工登记类型：归还 RETURNED 由归还入库过账自动生成，不在登记下拉中提供 */
const createTypes = computed(() =>
  Object.fromEntries(Object.entries(settlementTypeMap).filter(([value]) => value !== 'RETURNED'))
)

const pager = usePagedList((params) =>
  pageSettlements({
    ...params,
    issueLineId: query.issueLineId || undefined
  })
)

const createVisible = ref(false)
const submitting = ref(false)
const form = reactive({ issueLineId: '', settlementType: 'CONSUMED', quantity: null, reason: '' })

/** 可核销出库记录选项（对应库存流水中的出库；远程搜索物料编码/名称） */
const issueOptions = ref([])
const optionsLoading = ref(false)
const selectedIssue = computed(() =>
  issueOptions.value.find((o) => o.issueLineId === form.issueLineId) || null
)
const maxSettleQty = computed(() =>
  selectedIssue.value ? Number(selectedIssue.value.remainQty) : undefined
)

function issueOptionLabel(opt) {
  const remain = Number(opt.remainQty) <= 0 ? '已核销完' : `剩余 ${formatQty(opt.remainQty)}`
  const course = opt.courseName ? `${opt.courseName} · ` : ''
  return `${course}${opt.skuCode} · ${opt.materialName} · 出库 ${formatQty(opt.quantity)} · ${remain}`
}

async function searchIssueOptions(keyword) {
  optionsLoading.value = true
  try {
    const res = await pageIssueOptions({ pageNum: 1, pageSize: 50, keyword: keyword || undefined })
    issueOptions.value = res.records
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    optionsLoading.value = false
  }
}

function onIssueChange() {
  form.quantity = null
}

function openCreate() {
  form.issueLineId = ''
  form.settlementType = 'CONSUMED'
  form.quantity = null
  form.reason = ''
  createVisible.value = true
  if (!issueOptions.value.length) {
    searchIssueOptions('')
  }
}

async function submitCreate() {
  if (!form.issueLineId) {
    ElMessage.warning('请选择要核销的出库记录')
    return
  }
  if (!selectedIssue.value || Number(selectedIssue.value.remainQty) <= 0) {
    ElMessage.warning('该出库记录已核销完，请选择其他记录')
    return
  }
  if (form.quantity === null || form.quantity <= 0) {
    ElMessage.warning('核销数量必须大于 0')
    return
  }
  if (form.quantity > Number(selectedIssue.value.remainQty)) {
    ElMessage.warning('核销数量不能超过剩余可核销量')
    return
  }
  submitting.value = true
  try {
    await createSettlement({
      issueLineId: form.issueLineId,
      settlementType: form.settlementType,
      quantity: form.quantity,
      reason: form.reason || undefined
    })
    ElMessage.success('核销成功')
    createVisible.value = false
    pager.load()
    // 刷新选项的剩余可核销量（重新拉取当前关键词结果）
    searchIssueOptions('')
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(pager.load)

defineExpose({ refresh: pager.load })
</script>

<style scoped>
.issue-option-select {
  width: 100%;
}

.issue-brief {
  margin-bottom: 12px;
}

.remain-hint {
  margin-left: 8px;
}
</style>
