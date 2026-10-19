<template>
  <div>
    <div class="toolbar">
      <el-select
        v-model="query.status"
        clearable
        placeholder="全部状态"
        @change="pager.search"
      >
        <el-option
          label="全部状态"
          value=""
        />
        <el-option
          v-for="(cfg, value) in stocktakeStatusMap"
          :key="value"
          :label="cfg.label"
          :value="value"
        />
      </el-select>
      <LocationSelect
        v-model="query.locationId"
        placeholder="全部库位"
        all-option
        @change="pager.search"
      />
      <el-button
        v-permission="'item:stocktake:create'"
        type="primary"
        @click="openCreate"
      >
        新建盘点单
      </el-button>
      <el-button @click="pager.search">
        查询
      </el-button>
      <el-dropdown
        v-permission="'item:stocktake:export'"
        @command="doExport"
      >
        <el-button>
          导出<el-icon class="el-icon--right">
            <arrow-down />
          </el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="xlsx">
              Excel（.xlsx）
            </el-dropdown-item>
            <el-dropdown-item command="csv">
              CSV（.csv）
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-table
      v-loading="pager.loading"
      :data="pager.rows"
      border
    >
      <el-table-column
        prop="id"
        label="盘点单 ID"
        width="190"
        show-overflow-tooltip
      />
      <el-table-column
        prop="locationName"
        label="库位"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="countDate"
        label="盘点日期"
        width="120"
      />
      <el-table-column
        label="状态"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.status"
            :map="stocktakeStatusMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="adjustDocumentId"
        label="调账单 ID"
        width="190"
        show-overflow-tooltip
      />
      <el-table-column
        label="开始时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.startedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="400"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openDetail(row.id)"
          >
            详情
          </el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            v-permission="'item:stocktake:create'"
            link
            type="success"
            @click="doStart(row)"
          >
            开始盘点
          </el-button>
          <el-button
            v-if="row.status === 'COUNTING'"
            v-permission="'item:stocktake:count'"
            link
            type="primary"
            @click="openCount(row)"
          >
            实盘录入
          </el-button>
          <el-button
            v-if="row.status === 'COUNTING'"
            v-permission="'item:stocktake:count'"
            link
            type="primary"
            @click="openCountImport(row)"
          >
            批量回导
          </el-button>
          <el-button
            v-if="row.status === 'COUNTING'"
            v-permission="'item:stocktake:complete'"
            link
            type="warning"
            @click="doSubmitReview(row)"
          >
            提交复核
          </el-button>
          <el-button
            v-if="row.status === 'PENDING_REVIEW' && row.adjustDocumentId"
            v-permission="'item:adjust:approve'"
            link
            type="success"
            @click="doApproveAdjust(row)"
          >
            调账审批
          </el-button>
          <el-button
            v-if="row.status === 'PENDING_REVIEW' && !row.adjustDocumentId"
            v-permission="'item:stocktake:complete'"
            link
            type="success"
            @click="doComplete(row)"
          >
            确认完成
          </el-button>
          <el-button
            v-if="row.status === 'DRAFT' || row.status === 'COUNTING'"
            v-permission="'item:stocktake:create'"
            link
            type="danger"
            @click="doCancel(row)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 盘点详情 -->
    <el-dialog
      v-model="detailVisible"
      title="盘点单详情"
      width="1000px"
    >
      <el-descriptions
        v-if="detail"
        :column="3"
        border
      >
        <el-descriptions-item label="盘点单 ID">
          {{ detail.id }}
        </el-descriptions-item>
        <el-descriptions-item label="库位">
          {{ detail.locationName }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag
            :value="detail.status"
            :map="stocktakeStatusMap"
          />
        </el-descriptions-item>
        <el-descriptions-item label="盘点日期">
          {{ detail.countDate }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ fmtShanghai(detail.startedAt) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="调账单 ID">
          {{ detail.adjustDocumentId || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table
        v-if="detail"
        :data="detail.lines"
        border
        class="mt8"
      >
        <el-table-column
          prop="skuCode"
          label="SKU 码"
          width="170"
          show-overflow-tooltip
        />
        <el-table-column
          label="物料"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.skuName }}（{{ row.baseUnit }}）
          </template>
        </el-table-column>
        <el-table-column
          label="账面快照"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.bookQtySnapshot) }}
          </template>
        </el-table-column>
        <el-table-column
          label="实盘量"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.countedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="差异"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            <span
              v-if="row.differenceQty !== null && Number(row.differenceQty) !== 0"
              :class="Number(row.differenceQty) > 0 ? 'pos' : 'neg'"
            >
              {{ formatQty(row.differenceQty) }}
            </span>
            <span v-else>0</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="differenceReason"
          label="差异原因"
          min-width="150"
          show-overflow-tooltip
        />
      </el-table>
    </el-dialog>

    <!-- 新建盘点 -->
    <el-dialog
      v-model="createVisible"
      title="新建盘点单（登记时冻结库位全部余额快照）"
      width="560px"
    >
      <el-form label-width="100px">
        <el-form-item
          label="盘点库位"
          required
        >
          <LocationSelect
            v-model="createForm.locationId"
            placeholder="选择库位"
          />
        </el-form-item>
        <el-form-item
          label="盘点日期"
          required
        >
          <el-date-picker
            v-model="createForm.countDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
      </el-form>
      <div class="muted small">
        开始盘点后库位被冻结，该库位的单据过账将被拒绝（40900）；盘点完成/取消后解冻。
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

    <!-- 实盘录入 -->
    <el-dialog
      v-model="countVisible"
      title="实盘录入"
      width="900px"
    >
      <el-table
        :data="countRows"
        border
        max-height="420"
      >
        <el-table-column
          prop="skuCode"
          label="SKU 码"
          width="160"
          show-overflow-tooltip
        />
        <el-table-column
          prop="skuName"
          label="物料"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column
          label="账面快照"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.bookQtySnapshot) }}
          </template>
        </el-table-column>
        <el-table-column
          label="实盘量"
          width="160"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.countedQty"
              :min="0"
              :step="1"
              :precision="3"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="差异原因"
          min-width="170"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.differenceReason"
              placeholder="实盘与账面不一致时必填"
            />
          </template>
        </el-table-column>
      </el-table>
      <div class="muted small mt8">
        行标识 ID：{{ countStocktakeId }}（行已按库位快照生成，全部行录入后方可提交复核）
      </div>
      <template #footer>
        <el-button @click="countVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCount"
        >
          保存实盘
        </el-button>
      </template>
    </el-dialog>

    <!-- 盘点实盘批量回导 -->
    <ImportDialog
      ref="importDialogRef"
      title="批量回导实盘"
      tips="建议先用下方「下载模板」获取当前盘点单文件（已录入行带原值，支持中断续盘），填写实盘数与差异原因后回传；按 SKU 码定位行，实盘数与差异原因均空的行自动跳过（支持分批回导）。"
      :upload-fn="uploadCountImport"
      :template-fn="templateCountImport"
      fallback-template-name="盘点快照"
      @imported="pager.load"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import {
  approveAdjustStocktake,
  cancelStocktake,
  completeStocktake,
  countStocktake,
  createStocktake,
  exportStocktakeSnapshot,
  exportStocktakes,
  getStocktake,
  importStocktakeCount,
  pageStocktakes,
  startStocktake,
  submitReviewStocktake
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { saveBlobFile } from '@/utils/download'
import { stocktakeStatusMap } from '@/utils/statusMap'
import { fmtShanghai, formatQty, todayShanghai } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import LocationSelect from '@/components/LocationSelect.vue'
import ImportDialog from '@/components/ImportDialog.vue'

const query = reactive({ status: null, locationId: null })

const pager = usePagedList((params) =>
  pageStocktakes({
    ...params,
    status: query.status || undefined,
    locationId: query.locationId || undefined
  })
)

const detailVisible = ref(false)
const detail = ref(null)

const createVisible = ref(false)
const submitting = ref(false)
const createForm = reactive({ locationId: null, countDate: '' })

const countVisible = ref(false)
const countStocktakeId = ref(null)
const countRows = ref([])

const importDialogRef = ref(null)
const countImportId = ref(null)

function openCountImport(row) {
  countImportId.value = row.id
  importDialogRef.value.open()
}

function uploadCountImport(file) {
  return importStocktakeCount(countImportId.value, file)
}

function templateCountImport(format) {
  return exportStocktakeSnapshot(countImportId.value, format)
}

async function doExport(format) {
  try {
    const file = await exportStocktakes({
      status: query.status || undefined,
      locationId: query.locationId || undefined,
      format
    })
    saveBlobFile(file, `盘点单导出.${format}`)
    ElMessage.success('导出成功，文件已开始下载')
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function openDetail(id) {
  try {
    detail.value = await getStocktake(id)
    detailVisible.value = true
  } catch {
    // 失败已由全局拦截器提示
  }
}

function openCreate() {
  createForm.locationId = null
  createForm.countDate = todayShanghai()
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.locationId) {
    ElMessage.warning('请选择盘点库位')
    return
  }
  if (!createForm.countDate) {
    ElMessage.warning('请选择盘点日期')
    return
  }
  submitting.value = true
  try {
    const id = await createStocktake({
      locationId: createForm.locationId,
      countDate: createForm.countDate
    })
    ElMessage.success(`已创建盘点单 ${id}（草稿），开始盘点后冻结库位`)
    createVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doStart(row) {
  const ok = await confirmAction(
    `开始盘点「${row.locationName}」？开始后库位被冻结，该库位单据过账将被拒绝，直至盘点完成或取消。`,
    '开始盘点确认'
  )
  if (!ok) return
  try {
    await startStocktake(row.id)
    ElMessage.success('已开始盘点，库位已冻结')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function openCount(row) {
  try {
    const data = await getStocktake(row.id)
    countStocktakeId.value = row.id
    countRows.value = data.lines.map((l) => ({
      id: l.id,
      skuCode: l.skuCode,
      skuName: l.skuName,
      bookQtySnapshot: l.bookQtySnapshot,
      countedQty: l.countedQty === null ? null : l.countedQty,
      differenceReason: l.differenceReason || ''
    }))
    countVisible.value = true
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function submitCount() {
  for (const row of countRows.value) {
    if (row.countedQty === null || row.countedQty === undefined) {
      ElMessage.warning('存在未录入实盘量的行')
      return
    }
  }
  submitting.value = true
  try {
    await countStocktake(countStocktakeId.value, {
      lines: countRows.value.map((r) => ({
        lineId: r.id,
        countedQty: r.countedQty,
        differenceReason: r.differenceReason || undefined
      }))
    })
    ElMessage.success('实盘已保存')
    countVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doSubmitReview(row) {
  const ok = await confirmAction('将校验余额版本一致性，差异行生成调账草稿单；确认提交？', '提交复核')
  if (!ok) return
  try {
    const adjustId = await submitReviewStocktake(row.id)
    if (adjustId) {
      ElMessage.success(`已提交复核，差异调账单：${adjustId}，请执行「调账审批」`)
    } else {
      ElMessage.success('已提交复核，无差异，可确认完成')
    }
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doApproveAdjust(row) {
  const ok = await confirmAction(
    '将审批并过账调账单（盘盈按账面均价入账、盘亏按快照回填出账），确认后盘点完成、库位解冻。',
    '调账审批'
  )
  if (!ok) return
  try {
    await approveAdjustStocktake(row.id)
    ElMessage.success('调账已审批过账，盘点完成')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doComplete(row) {
  const ok = await confirmAction('确认完成该盘点单？完成后库位解冻，不可再修改。', '确认完成')
  if (!ok) return
  try {
    await completeStocktake(row.id)
    ElMessage.success('盘点已完成')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doCancel(row) {
  const ok = await confirmAction('确认取消该盘点单？取消后库位解冻。', '取消确认')
  if (!ok) return
  try {
    await cancelStocktake(row.id)
    ElMessage.success('已取消盘点')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

onMounted(pager.load)

defineExpose({ refresh: pager.load })
</script>

<style scoped>
.pos {
  color: #67c23a;
  font-weight: 600;
}

.neg {
  color: #f56c6c;
  font-weight: 600;
}
</style>
