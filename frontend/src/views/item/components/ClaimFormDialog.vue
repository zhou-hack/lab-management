<template>
  <el-dialog
    :model-value="modelValue"
    width="1080px"
    top="4vh"
    class="claim_form_dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :before-close="beforeClose"
    @update:model-value="requestClose"
  >
    <template #header>
      <div class="claim_form__header">
        <h3 class="t-title">
          {{ dialogTitle }}
        </h3>
        <p class="t-sub">
          基本信息 → 库存选购 → 计划确认；预约日期仅支持上海当天至 +3 天
        </p>
      </div>
    </template>

    <div
      v-loading="initializing"
      class="claim_form"
    >
      <el-steps
        :active="step - 1"
        finish-status="success"
        align-center
        class="claim_form__steps"
      >
        <el-step
          title="基本信息"
          description="用途、日期与节次"
        />
        <el-step
          title="库存选购"
          description="目录与购物车"
        />
        <el-step
          title="计划确认"
          description="分配、缺口与风险"
        />
      </el-steps>

      <el-alert
        v-if="loadError"
        type="error"
        :closable="false"
        show-icon
        :title="loadError"
        class="claim_form__alert"
      />
      <el-alert
        v-if="conflictMessage"
        type="warning"
        :closable="false"
        show-icon
        class="claim_form__alert"
      >
        <template #title>
          提交时库存或单据版本已变化
        </template>
        <div>{{ conflictMessage }}</div>
        <div>已重新获取计划预览，请核对最新缺口和分配后重试；若为版本冲突，请重新打开原申领。</div>
      </el-alert>
      <el-alert
        v-if="previewError"
        type="error"
        :closable="false"
        show-icon
        :title="previewError"
        class="claim_form__alert"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <section
          v-show="step === 1"
          class="claim_form__section"
        >
          <div class="claim_form__plan card">
            <div>
              <h4 class="claim_form__section_title">
                从我的预设方案载入
              </h4>
              <p class="t-hint">
                只复制方案中的物料和默认数量，不会带入原申领时间或分配。
              </p>
            </div>
            <div class="claim_form__plan_actions">
              <el-select
                v-model="selectedPlanId"
                placeholder="选择启用中的方案"
                clearable
                class="claim_form__plan_select"
              >
                <el-option
                  v-for="plan in plans"
                  :key="plan.id"
                  :label="plan.planName"
                  :value="String(plan.id)"
                />
              </el-select>
              <el-button
                :disabled="!selectedPlan"
                @click="applySelectedPlan"
              >
                载入物料
              </el-button>
            </div>
          </div>

          <el-form-item
            label="用途"
            prop="purpose"
          >
            <el-input
              v-model="form.purpose"
              type="textarea"
              :rows="3"
              maxlength="512"
              show-word-limit
              placeholder="请说明课程、实验或借用用途"
              @input="invalidatePreview"
            />
          </el-form-item>

          <div class="claim_form__basic_grid">
            <el-form-item
              label="预约日期"
              prop="bookingDate"
            >
              <el-date-picker
                v-model="form.bookingDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                :disabled-date="disableBookingDate"
                class="claim_form__full"
                @change="handleBookingChange"
              />
            </el-form-item>
            <el-form-item
              label="起始节次"
              prop="startPeriodId"
            >
              <el-select
                v-model="form.startPeriodId"
                placeholder="选择起始节次"
                class="claim_form__full"
                @change="handleBookingChange"
              >
                <el-option
                  v-for="period in periods"
                  :key="period.id"
                  :label="periodLabel(period)"
                  :value="String(period.id)"
                />
              </el-select>
            </el-form-item>
            <el-form-item
              label="结束节次"
              prop="endPeriodId"
            >
              <el-select
                v-model="form.endPeriodId"
                placeholder="选择结束节次"
                class="claim_form__full"
                @change="handleBookingChange"
              >
                <el-option
                  v-for="period in periods"
                  :key="period.id"
                  :label="periodLabel(period)"
                  :value="String(period.id)"
                />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <section
          v-show="step === 2"
          class="claim_form__section"
        >
          <MaterialCatalogCart
            v-model="lines"
            mode="claim"
            :load-catalog="loadClaimCatalog"
            :active="modelValue && step === 2"
            :refresh-key="catalogRefreshKey"
            quantity-field="requestedQuantity"
            @change="invalidatePreview"
          />
        </section>

        <section
          v-show="step === 3"
          class="claim_form__section"
        >
          <template v-if="preview">
            <el-descriptions
              v-if="preview.bookingSnapshot"
              :column="3"
              border
              class="claim_form__snapshot"
            >
              <el-descriptions-item label="预约日期">
                {{ preview.bookingSnapshot.bookingDate }}
              </el-descriptions-item>
              <el-descriptions-item
                label="预约时间"
                :span="2"
              >
                {{ formatRange(preview.bookingSnapshot.bookingStartAt, preview.bookingSnapshot.bookingEndAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="最早实发时间">
                {{ formatInstant(preview.bookingSnapshot.issueEarliestAt) || '—' }}
              </el-descriptions-item>
              <el-descriptions-item
                label="归还期限"
                :span="2"
              >
                {{ formatInstant(preview.bookingSnapshot.returnDeadline) || '—' }}
              </el-descriptions-item>
            </el-descriptions>

            <h4 class="claim_form__section_title">
              逐项可约量与预占策略
            </h4>
            <el-table
              :data="preview.lines || []"
              class="table-scroll-x claim_form__table"
              row-key="materialId"
            >
              <el-table-column
                label="物料"
                min-width="180"
              >
                <template #default="{ row }">
                  <div class="claim_form__material_name">
                    {{ row.materialName }}
                  </div>
                  <div class="t-hint">
                    {{ row.specification || materialTypeText(row.materialType) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="申请"
                min-width="100"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.requestedQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="当前可约合计"
                min-width="120"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.totalAvailableQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="预占策略"
                min-width="190"
              >
                <template #default="{ row }">
                  <div>{{ reservationRuleLabel(row.reservationRule) }}</div>
                  <div
                    v-if="row.ownedAssetTotal !== null"
                    class="t-hint"
                  >
                    全库保有 {{ row.ownedAssetTotal }} 台
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="预占窗口"
                min-width="240"
              >
                <template #default="{ row }">
                  {{ formatRange(row.reservationStartAt, row.reservationEndAt) }}
                </template>
              </el-table-column>
            </el-table>

            <div class="claim_form__allocation_head">
              <div>
                <h4 class="claim_form__section_title">
                  计划分配
                </h4>
                <p class="t-hint">
                  可调整物料、库位和数量；每项物料的分配合计必须等于申请数量。
                </p>
              </div>
              <el-button @click="addAllocation">
                添加分配
              </el-button>
            </div>
            <el-table
              :data="editableAllocations"
              class="table-scroll-x claim_form__table"
              empty-text="请添加计划分配"
            >
              <el-table-column
                label="物料"
                min-width="170"
              >
                <template #default="{ row }">
                  <el-select
                    v-model="row.materialId"
                    placeholder="选择物料"
                    @change="onAllocationMaterialChange(row)"
                  >
                    <el-option
                      v-for="line in preview.lines || []"
                      :key="line.materialId"
                      :label="line.materialName"
                      :value="String(line.materialId)"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column
                label="库位"
                min-width="190"
              >
                <template #default="{ row }">
                  <ItemLocationSelect
                    v-model="row.locationId"
                    for-claim
                    :current-option="{
                      id: row.locationId,
                      locationName: row.locationName,
                      locationCode: row.locationCode,
                      enabled: 1
                    }"
                    :clearable="false"
                    @change="(id, option) => onAllocationLocationChange(row, option)"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="计划数量"
                min-width="150"
              >
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.plannedQuantity"
                    :min="1"
                    :precision="0"
                    :step="1"
                    :controls="false"
                    class="claim_form__allocation_number"
                    @change="markPreviewDirty"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="该库位可约"
                min-width="120"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText :value="row.availableQuantity" />
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                width="70"
                fixed="right"
              >
                <template #default="{ $index }">
                  <el-button
                    link
                    type="danger"
                    @click="removeAllocation($index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="claim_form__allocation_actions">
              <el-alert
                v-if="previewDirty"
                title="计划已调整，必须重新预览后才能提交"
                type="warning"
                :closable="false"
                show-icon
                class="claim_form__dirty_alert"
              />
              <el-button
                type="primary"
                plain
                :loading="previewLoading"
                @click="generatePreview"
              >
                重新预览
              </el-button>
            </div>

            <template v-if="preview.shortages?.length">
              <h4 class="claim_form__section_title claim_form__shortage_title">
                库存缺口（不足时禁止提交）
              </h4>
              <StockShortageTable :items="preview.shortages" />
            </template>
            <el-alert
              v-else
              title="当前计划无库存缺口，可以提交审核"
              type="success"
              :closable="false"
              show-icon
              class="claim_form__alert"
            />

            <el-alert
              v-if="preview.warnings?.length"
              title="预约与库存风险"
              type="warning"
              :closable="false"
              show-icon
              class="claim_form__alert"
            >
              <ul class="claim_form__warnings">
                <li
                  v-for="warning in preview.warnings"
                  :key="warning"
                >
                  {{ warning }}
                </li>
              </ul>
            </el-alert>
          </template>
          <el-empty
            v-else-if="!previewLoading"
            description="请返回上一步生成计划预览"
            :image-size="72"
          />
        </section>
      </el-form>
    </div>

    <template #footer>
      <div class="claim_form__footer">
        <el-button
          :disabled="submitting"
          @click="requestClose(false)"
        >
          取消
        </el-button>
        <div class="claim_form__footer_actions">
          <el-button
            v-if="step > 1"
            :disabled="previewLoading || submitting"
            @click="step -= 1"
          >
            上一步
          </el-button>
          <el-button
            v-if="step === 1"
            type="primary"
            :disabled="initializing || !!loadError"
            @click="goToMaterials"
          >
            下一步：库存选购
          </el-button>
          <el-button
            v-if="step === 2"
            type="primary"
            :loading="previewLoading"
            @click="generatePreview"
          >
            去结算
          </el-button>
          <el-button
            v-if="step === 3"
            type="primary"
            :disabled="!canSubmit"
            :loading="submitting"
            @click="submit"
          >
            {{ submitText }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import MaterialCatalogCart from './MaterialCatalogCart.vue'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import StockShortageTable from '@/components/item/StockShortageTable.vue'
import { reservationRuleLabel } from './claim.constants'
import {
  buildClaimPayload,
  buildClaimSubmissionPayload,
  getClaimAllocationsError,
  getClaimLinesError,
  shouldRefreshClaimPreview
} from './claim.rules'
import { listItemPeriods } from '@/api/item/period'
import { getMaterialPlan, listMaterialPlans } from '@/api/item/plan'
import {
  createClaim,
  getClaimAvailability,
  getClaimDetail,
  getClaimTemplate,
  previewClaim,
  updateClaim
} from '@/api/item/claim'
import { addDaysShanghai, formatInstant, formatRange, todayShanghai } from '@/utils/datetime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  mode: {
    type: String,
    default: 'create',
    validator: (value) => ['create', 'edit', 'rebook'].includes(value)
  },
  claimId: { type: [String, Number], default: '' },
  planId: { type: [String, Number], default: '' }
})

const emit = defineEmits(['close', 'saved'])

const today = todayShanghai()
const maxBookingDate = addDaysShanghai(today, 3)
const formRef = ref()
const step = ref(1)
const periods = ref([])
const plans = ref([])
const selectedPlanId = ref('')
const selectedPlanDetail = ref(null)
const lines = ref([])
const preview = ref(null)
const editableAllocations = ref([])
const initialAllocations = ref([])
const useInitialAllocations = ref(false)
const revisionNo = ref(null)
const initializing = ref(false)
const previewLoading = ref(false)
const submitting = ref(false)
const previewDirty = ref(false)
const loadError = ref('')
const previewError = ref('')
const conflictMessage = ref('')
const idempotencyKey = ref('')
let initializeSerial = 0

const form = reactive({
  purpose: '',
  bookingDate: today,
  startPeriodId: '',
  endPeriodId: ''
})

const rules = {
  purpose: [
    { required: true, message: '请填写用途', trigger: 'blur' },
    { max: 512, message: '用途不能超过 512 字', trigger: 'blur' }
  ],
  bookingDate: [{ required: true, message: '请选择预约日期', trigger: 'change' }],
  startPeriodId: [{ required: true, message: '请选择起始节次', trigger: 'change' }],
  endPeriodId: [{ required: true, message: '请选择结束节次', trigger: 'change' }]
}

const dialogTitle = computed(() => ({
  create: '新建物料申领',
  edit: '修改物料申领',
  rebook: '再次预约物料'
}[props.mode]))

const submitText = computed(() => props.mode === 'edit' ? '保存并重新审核' : '提交申领')
const selectedPlan = computed(() => {
  const id = String(selectedPlanId.value || '')
  return plans.value.find((plan) => String(plan.id) === id)
    || (String(selectedPlanDetail.value?.id || '') === id ? selectedPlanDetail.value : null)
})
const canSubmit = computed(() => !initializing.value && !previewLoading.value && !submitting.value
  && !!preview.value?.valid && !previewDirty.value && !preview.value?.shortages?.length
  && editableAllocations.value.length > 0)
const catalogRefreshKey = computed(() => [
  form.bookingDate,
  form.startPeriodId,
  form.endPeriodId,
  props.mode === 'edit' ? props.claimId : ''
].join('|'))

function emptyLine() {
  return {
    materialId: '',
    materialName: '',
    materialType: '',
    quantityMode: '',
    baseUnit: '',
    specification: '',
    requestedQuantity: null
  }
}

function periodLabel(period) {
  return `${period.periodName}（${period.startClock}-${period.endClock}）`
}

function materialTypeText(type) {
  return type === 'FIXED_ASSET' ? '固定资产' : type === 'CONSUMABLE' ? '耗材' : ''
}

function disableBookingDate(date) {
  const start = Date.parse(`${today}T00:00:00Z`)
  const end = Date.parse(`${maxBookingDate}T23:59:59Z`)
  const value = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate())
  return value < start || value > end
}

function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `claim-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function normalizeLines(sourceLines, field = 'requestedQuantity') {
  return (sourceLines || []).map((line) => ({
    materialId: String(line.materialId),
    materialName: line.materialName || '',
    materialType: line.materialType || '',
    quantityMode: line.quantityMode || '',
    baseUnit: line.baseUnit || '',
    specification: line.specification || '',
    requestedQuantity: Number(line[field])
  }))
}

function resetForm() {
  step.value = 1
  form.purpose = ''
  form.bookingDate = today
  form.startPeriodId = ''
  form.endPeriodId = ''
  lines.value = []
  selectedPlanId.value = props.planId ? String(props.planId) : ''
  selectedPlanDetail.value = null
  preview.value = null
  editableAllocations.value = []
  initialAllocations.value = []
  useInitialAllocations.value = false
  revisionNo.value = null
  previewDirty.value = false
  loadError.value = ''
  previewError.value = ''
  conflictMessage.value = ''
  idempotencyKey.value = props.mode === 'edit' ? '' : newIdempotencyKey()
  formRef.value?.clearValidate()
}

async function initialize() {
  const serial = ++initializeSerial
  resetForm()
  initializing.value = true
  try {
    const sourceRequest = props.mode === 'edit'
      ? getClaimDetail(String(props.claimId))
      : props.mode === 'rebook'
        ? getClaimTemplate(String(props.claimId))
        : props.planId
          ? getMaterialPlan(String(props.planId))
          : Promise.resolve(null)
    const [periodResult, planResult, source] = await Promise.all([
      listItemPeriods(),
      listMaterialPlans(1).catch(() => []),
      sourceRequest
    ])
    if (serial !== initializeSerial) return
    periods.value = (periodResult || []).map((period) => ({ ...period, id: String(period.id) }))
    plans.value = (planResult || []).map((plan) => ({ ...plan, id: String(plan.id) }))

    if (props.mode === 'edit') {
      form.purpose = source.purpose || ''
      form.bookingDate = source.bookingDate || today
      form.startPeriodId = String(source.startPeriodId || '')
      form.endPeriodId = String(source.endPeriodId || '')
      lines.value = normalizeLines(source.lines)
      revisionNo.value = source.revisionNo
      initialAllocations.value = (source.allocations || []).map((allocation) => ({
        materialId: String(allocation.materialId),
        locationId: String(allocation.locationId),
        plannedQuantity: Number(allocation.plannedQuantity)
      }))
      useInitialAllocations.value = initialAllocations.value.length > 0
    } else if (props.mode === 'rebook') {
      form.purpose = source.purpose || ''
      lines.value = normalizeLines(source.lines)
    } else if (source) {
      selectedPlanDetail.value = source
      applyPlan(source)
    }
  } catch (error) {
    if (serial !== initializeSerial) return
    loadError.value = error?.response?.data?.msg || error?.message || '表单初始化失败，请关闭后重试'
  } finally {
    if (serial === initializeSerial) initializing.value = false
  }
}

function applyPlan(plan) {
  if (!plan) return
  lines.value = normalizeLines(plan.lines, 'defaultQuantity')
  if (!lines.value.length) lines.value = [emptyLine()]
  invalidatePreview()
  ElMessage.success(`已载入方案「${plan.planName}」的 ${plan.lines?.length || 0} 项物料`)
}

function applySelectedPlan() {
  applyPlan(selectedPlan.value)
}

function invalidatePreview() {
  preview.value = null
  editableAllocations.value = []
  previewDirty.value = false
  useInitialAllocations.value = false
  conflictMessage.value = ''
  previewError.value = ''
}

function loadClaimCatalog(params) {
  return getClaimAvailability({
    ...params,
    bookingDate: form.bookingDate,
    startPeriodId: String(form.startPeriodId),
    endPeriodId: String(form.endPeriodId),
    claimId: props.mode === 'edit' && props.claimId ? String(props.claimId) : undefined
  })
}

function handleBookingChange() {
  invalidatePreview()
}

async function validateBasic() {
  try {
    await formRef.value.validate()
  } catch {
    return false
  }
  if (!form.bookingDate || form.bookingDate < today || form.bookingDate > maxBookingDate) {
    ElMessage.warning(`预约日期仅允许为 ${today} 至 ${maxBookingDate}`)
    return false
  }
  const start = periods.value.find((period) => String(period.id) === String(form.startPeriodId))
  const end = periods.value.find((period) => String(period.id) === String(form.endPeriodId))
  if (!start || !end) {
    ElMessage.warning('请选择有效的起止节次')
    return false
  }
  if (Number(start.periodNo) > Number(end.periodNo)) {
    ElMessage.warning('起始节次不能晚于结束节次')
    return false
  }
  return true
}

function validateLines() {
  const error = getClaimLinesError(lines.value)
  if (error) {
    ElMessage.warning(error)
    return false
  }
  return true
}

function buildPayload(allocations) {
  return buildClaimPayload(form, lines.value, allocations)
}

function validateAllocations() {
  const error = getClaimAllocationsError(
    editableAllocations.value,
    lines.value
  )
  if (error) {
    ElMessage.warning(error)
    return false
  }
  return true
}

async function generatePreview() {
  if (previewLoading.value) return false
  if (!await validateBasic()) {
    step.value = 1
    return false
  }
  if (!validateLines()) {
    step.value = 2
    return false
  }
  const fromConfirmation = step.value === 3
  if (fromConfirmation && !validateAllocations()) return false
  const allocations = fromConfirmation
    ? editableAllocations.value
    : useInitialAllocations.value ? initialAllocations.value : []

  previewLoading.value = true
  previewError.value = ''
  try {
    const result = await previewClaim(buildPayload(allocations))
    preview.value = result
    editableAllocations.value = (result?.allocations || []).map((allocation) => ({
      ...allocation,
      materialId: String(allocation.materialId),
      locationId: String(allocation.locationId),
      plannedQuantity: Number(allocation.plannedQuantity),
      availableQuantity: allocation.availableQuantity
    }))
    previewDirty.value = false
    useInitialAllocations.value = false
    step.value = 3
    return true
  } catch (error) {
    previewError.value = error?.response?.data?.msg || error?.message || '计划预览失败，请调整后重试'
    return false
  } finally {
    previewLoading.value = false
  }
}

async function goToMaterials() {
  if (await validateBasic()) step.value = 2
}

function markPreviewDirty() {
  previewDirty.value = true
  conflictMessage.value = ''
}

function onAllocationMaterialChange(row) {
  row.materialName = preview.value?.lines?.find((line) => String(line.materialId) === String(row.materialId))
    ?.materialName || ''
  row.availableQuantity = null
  markPreviewDirty()
}

function onAllocationLocationChange(row, option) {
  row.locationName = option?.locationName || ''
  row.locationCode = option?.locationCode || ''
  row.availableQuantity = null
  markPreviewDirty()
}

function addAllocation() {
  const first = preview.value?.lines?.[0]
  if (!first) return
  editableAllocations.value.push({
    materialId: String(first.materialId),
    materialName: first.materialName,
    locationId: '',
    locationName: '',
    locationCode: '',
    plannedQuantity: null,
    availableQuantity: null
  })
  markPreviewDirty()
}

function removeAllocation(index) {
  editableAllocations.value.splice(index, 1)
  markPreviewDirty()
}

async function submit() {
  if (previewDirty.value) {
    ElMessage.warning('计划已调整，请先重新预览')
    return
  }
  if (!canSubmit.value) {
    ElMessage.warning('当前计划存在缺口或尚未完成预览，不能提交')
    return
  }
  if (!validateLines() || !validateAllocations()) return
  submitting.value = true
  conflictMessage.value = ''
  try {
    const payload = buildClaimSubmissionPayload(props.mode, buildPayload(editableAllocations.value), {
      revisionNo: revisionNo.value,
      idempotencyKey: idempotencyKey.value
    })
    const saved = props.mode === 'edit'
      ? await updateClaim(String(props.claimId), payload)
      : await createClaim(payload)
    ElMessage.success(props.mode === 'edit' ? '申领已修改并重新提交审核' : '申领已提交，等待审核')
    emit('saved', saved)
    emit('close')
  } catch (error) {
    if (shouldRefreshClaimPreview(error)) {
      conflictMessage.value = error?.response?.data?.msg || error?.message || '申领状态或库存已变化'
      step.value = 3
      await generatePreview()
    }
  } finally {
    submitting.value = false
  }
}

function requestClose(value) {
  if (!value && !submitting.value) emit('close')
}

function beforeClose(done) {
  if (!submitting.value) done()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) initialize()
    else initializeSerial += 1
  }
)
</script>

<style scoped>
.claim_form__header h3 {
  margin: 0 0 5px;
  font-size: 18px;
}

.claim_form__header p {
  margin: 0;
}

.claim_form {
  min-height: 320px;
}

.claim_form__steps {
  margin-bottom: 22px;
}

.claim_form__section {
  min-height: 280px;
}

.claim_form__section_title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.claim_form__alert {
  margin-bottom: 14px;
}

.claim_form__plan {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  margin-bottom: 18px;
  background: var(--surface-sunken);
}

.claim_form__plan p {
  margin: 0;
}

.claim_form__plan_actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.claim_form__plan_select {
  width: 230px;
}

.claim_form__basic_grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.claim_form__full {
  width: 100%;
}

.claim_form__snapshot {
  margin-bottom: 20px;
}

.claim_form__table {
  margin-bottom: 20px;
}

.claim_form__material_name {
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.claim_form__allocation_head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.claim_form__allocation_number {
  width: 130px;
}

.claim_form__allocation_actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin: -8px 0 18px;
}

.claim_form__dirty_alert {
  flex: 1;
  margin: 0;
}

.claim_form__shortage_title {
  color: var(--danger);
}

.claim_form__warnings {
  padding-left: 18px;
  margin: 6px 0 0;
  line-height: 1.7;
}

.claim_form__footer,
.claim_form__footer_actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

@media (max-width: 760px) {
  .claim_form__plan,
  .claim_form__allocation_head {
    align-items: stretch;
    flex-direction: column;
  }

  .claim_form__plan_actions {
    width: 100%;
  }

  .claim_form__plan_select {
    flex: 1;
    width: auto;
  }

  .claim_form__basic_grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
