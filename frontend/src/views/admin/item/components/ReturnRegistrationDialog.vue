<template>
  <el-dialog
    v-model="visible"
    title="申领归还 / 出库核销"
    width="min(1180px, 96vw)"
    destroy-on-close
    @closed="reset"
  >
    <div
      v-loading="loading"
      class="return_dialog"
    >
      <template v-if="context">
        <el-descriptions
          :column="3"
          border
          size="small"
        >
          <el-descriptions-item label="来源出库单">
            {{ context.sourceDocumentNo }}
          </el-descriptions-item>
          <el-descriptions-item label="归还库位">
            {{ context.locationName }} · {{ context.locationCode }}
          </el-descriptions-item>
          <el-descriptions-item label="归还期限">
            {{ formatTime(context.returnDeadline) }}
          </el-descriptions-item>
          <el-descriptions-item
            label="业务链ID"
            :span="3"
          >
            {{ context.businessChainId }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="context.overdue"
          title="本次归还已逾期，必须填写逾期原因；历史逾期记录不会因归还完成而被隐藏。"
          type="warning"
          :closable="false"
          show-icon
          class="return_dialog__alert"
        />
        <el-alert
          v-if="!context.canReturn"
          :title="context.returnBlockedReason || '当前不可归还'"
          type="error"
          :closable="false"
          show-icon
          class="return_dialog__alert"
        />
        <p class="t-sub return_dialog__hint">
          默认按剩余数量全部回库；如有未归还物料，请调整回库数量。可多次归还，四类数量合计不得超过剩余量，消耗、丢失和损坏须分别填写原因。
        </p>
        <el-table
          :data="forms"
          row-key="sourceMaterialDocumentLineId"
          class="table-scroll-x"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <el-form
                label-position="top"
                class="return_dialog__reasons"
              >
                <el-form-item
                  v-if="Number(row.consumedQuantity) > 0"
                  label="教学消耗原因"
                  required
                >
                  <el-input
                    v-model="row.consumedReason"
                    type="textarea"
                    maxlength="512"
                    show-word-limit
                  />
                </el-form-item>
                <el-form-item
                  v-if="Number(row.lostQuantity) > 0"
                  label="丢失原因"
                  required
                >
                  <el-input
                    v-model="row.lostReason"
                    type="textarea"
                    maxlength="512"
                    show-word-limit
                  />
                </el-form-item>
                <el-form-item
                  v-if="Number(row.damagedQuantity) > 0"
                  label="损坏原因"
                  required
                >
                  <el-input
                    v-model="row.damagedReason"
                    type="textarea"
                    maxlength="512"
                    show-word-limit
                  />
                </el-form-item>
                <el-form-item
                  v-if="Number(row.returnQuantity) > 0"
                  label="回库存放位置"
                >
                  <el-input
                    v-model="row.positionText"
                    maxlength="256"
                    placeholder="默认为来源出库存放位置"
                  />
                </el-form-item>
                <p
                  v-if="!hasAnyQuantity(row)"
                  class="t-hint"
                >
                  填写本次处理数量后，在此补充对应原因和回库位置
                </p>
              </el-form>
            </template>
          </el-table-column>
          <el-table-column
            label="物料 / 资产"
            min-width="200"
          >
            <template #default="{ row }">
              <div class="return_dialog__material">
                <strong>{{ row.materialName }}</strong>
                <span class="t-hint">{{ row.assetCode || row.specification || row.materialId }}</span>
                <span class="t-hint">来源行 {{ row.sourceMaterialDocumentLineId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="实发 / 已还 / 已核销 / 剩余"
            min-width="230"
            align="right"
          >
            <template #default="{ row }">
              <ItemQuantityText
                :value="row.issuedQuantity"
                :unit="row.baseUnit"
              /> /
              <ItemQuantityText
                :value="row.returnedQuantity"
                :unit="row.baseUnit"
              /> /
              <ItemQuantityText
                :value="row.settledQuantity"
                :unit="row.baseUnit"
              /> /
              <strong><ItemQuantityText
                :value="row.remainingQuantity"
                :unit="row.baseUnit"
              /></strong>
            </template>
          </el-table-column>
          <el-table-column
            label="回库 return"
            width="140"
          >
            <template #default="{ row }">
              <el-input-number
                v-model="row.returnQuantity"
                :min="0"
                :max="Number(row.remainingQuantity)"
                :precision="0"
                :step="1"
                controls-position="right"
                class="return_dialog__number"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="消耗 consumed"
            width="140"
          >
            <template #default="{ row }">
              <el-input-number
                v-model="row.consumedQuantity"
                :min="0"
                :max="Number(row.remainingQuantity)"
                :precision="0"
                :step="1"
                controls-position="right"
                class="return_dialog__number"
                :disabled="row.materialType === 'FIXED_ASSET'"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="丢失 lost"
            width="140"
          >
            <template #default="{ row }">
              <el-input-number
                v-model="row.lostQuantity"
                :min="0"
                :max="Number(row.remainingQuantity)"
                :precision="0"
                :step="1"
                controls-position="right"
                class="return_dialog__number"
              />
            </template>
          </el-table-column>
          <el-table-column
            label="损坏 damaged"
            width="140"
          >
            <template #default="{ row }">
              <el-input-number
                v-model="row.damagedQuantity"
                :min="0"
                :max="Number(row.remainingQuantity)"
                :precision="0"
                :step="1"
                controls-position="right"
                class="return_dialog__number"
              />
            </template>
          </el-table-column>
        </el-table>
        <el-form
          label-position="top"
          class="return_dialog__footer_form"
        >
          <el-form-item
            v-if="context.overdue"
            label="逾期原因 overdueReason"
            required
          >
            <el-input
              v-model="overdueReason"
              type="textarea"
              maxlength="512"
              show-word-limit
              placeholder="必填，说明逾期归还原因"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="remark"
              maxlength="512"
              show-word-limit
              placeholder="本次归还备注（可选）"
            />
          </el-form-item>
        </el-form>
      </template>
      <el-empty
        v-else-if="!loading"
        description="未获取到可归还的出库单"
      />
    </div>
    <template #footer>
      <el-button @click="visible = false">
        取消
      </el-button>
      <el-button
        type="primary"
        :disabled="!context?.canReturn"
        :loading="submitting"
        @click="submit"
      >
        提交本次归还
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import { createClaimReturn, getReturnContext } from '@/api/item/workflow'
import { formatInstant } from '@/utils/datetime'
import { isReturnWithinRemaining } from '../inventory.rules'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  sourceDocument: { type: [Object, String, Number], default: null }
})
const emit = defineEmits(['update:modelValue', 'success'])
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const context = ref(null)
const forms = ref([])
const overdueReason = ref('')
const remark = ref('')
const loading = ref(false)
const submitting = ref(false)
let requestSerial = 0

const sourceId = computed(() => {
  const source = props.sourceDocument
  if (source && typeof source === 'object') return String(source.document?.id || source.id || '')
  return source ? String(source) : ''
})
const formatTime = (value) => formatInstant(value) || '—'
const hasAnyQuantity = (row) => ['returnQuantity', 'consumedQuantity', 'lostQuantity', 'damagedQuantity']
  .some((key) => Number(row[key]) > 0)

watch(visible, (value) => {
  if (value) loadContext()
})

function reset() {
  requestSerial += 1
  context.value = null
  forms.value = []
  overdueReason.value = ''
  remark.value = ''
  loading.value = false
  submitting.value = false
}

async function loadContext() {
  if (!sourceId.value) return
  const serial = ++requestSerial
  loading.value = true
  context.value = null
  try {
    const result = await getReturnContext(sourceId.value)
    if (serial !== requestSerial) return
    context.value = result
    forms.value = (result?.lines || []).map((line) => ({
      ...line,
      sourceMaterialDocumentLineId: String(line.sourceMaterialDocumentLineId),
      returnQuantity: Number(line.remainingQuantity || 0),
      consumedQuantity: 0,
      lostQuantity: 0,
      damagedQuantity: 0,
      consumedReason: '',
      lostReason: '',
      damagedReason: '',
      positionText: line.sourcePositionText || ''
    }))
  } finally {
    if (serial === requestSerial) loading.value = false
  }
}

function validate(lines) {
  if (!lines.length) {
    ElMessage.warning('请至少填写一条归还或核销处理行')
    return false
  }
  if (context.value.overdue && !overdueReason.value.trim()) {
    ElMessage.warning('逾期归还必须填写 overdueReason')
    return false
  }
  for (const row of forms.value) {
    const quantities = ['returnQuantity', 'consumedQuantity', 'lostQuantity', 'damagedQuantity']
      .map((key) => Number(row[key] || 0))
    const total = quantities.reduce((sum, quantity) => sum + quantity, 0)
    if (total <= 0) continue
    if (quantities.some((quantity) => !Number.isInteger(quantity))) {
      ElMessage.warning(`${row.materialName} 的归还或核销数量必须为整数`)
      return false
    }
    if (!isReturnWithinRemaining(row)) {
      ElMessage.warning(`${row.materialName} 的本次处理数量超过剩余量`)
      return false
    }
    if (row.materialType === 'FIXED_ASSET' && (total !== 1 || Number(row.consumedQuantity) > 0)) {
      ElMessage.warning(`${row.materialName} 为固定资产，须逐台处理且不能教学消耗`)
      return false
    }
    for (const [key, label] of [['consumed', '教学消耗'], ['lost', '丢失'], ['damaged', '损坏']]) {
      if (Number(row[`${key}Quantity`]) > 0 && !row[`${key}Reason`]?.trim()) {
        ElMessage.warning(`请填写 ${row.materialName} 的${label}原因`)
        return false
      }
    }
  }
  return true
}

async function submit() {
  const lines = forms.value.filter(hasAnyQuantity).map((row) => ({
    sourceMaterialDocumentLineId: String(row.sourceMaterialDocumentLineId),
    returnQuantity: Number(row.returnQuantity || 0),
    consumedQuantity: Number(row.consumedQuantity || 0),
    lostQuantity: Number(row.lostQuantity || 0),
    damagedQuantity: Number(row.damagedQuantity || 0),
    consumedReason: Number(row.consumedQuantity) > 0 ? row.consumedReason.trim() : undefined,
    lostReason: Number(row.lostQuantity) > 0 ? row.lostReason.trim() : undefined,
    damagedReason: Number(row.damagedQuantity) > 0 ? row.damagedReason.trim() : undefined,
    positionText: Number(row.returnQuantity) > 0 ? row.positionText || '' : undefined
  }))
  if (!validate(lines)) return
  submitting.value = true
  try {
    const result = await createClaimReturn(sourceId.value, {
      idempotencyKey: `claim-return-${sourceId.value}-${crypto.randomUUID()}`,
      remark: remark.value || undefined,
      overdueReason: context.value.overdue ? overdueReason.value.trim() : undefined,
      lines
    })
    ElMessage.success('本次归还已登记，可继续处理剩余数量')
    visible.value = false
    emit('success', result)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.return_dialog__alert { margin-top: 12px; }
.return_dialog__hint { margin: 14px 0 10px; }
.return_dialog__material { display: flex; flex-direction: column; gap: 3px; }
.return_dialog__number { width: 122px; }
.return_dialog__reasons { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; padding: 8px 20px; }
.return_dialog__footer_form { margin-top: 16px; }
</style>
