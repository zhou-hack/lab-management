<template>
  <el-dialog
    v-model="visible"
    title="申领实发"
    width="min(1120px, 96vw)"
    destroy-on-close
    @closed="reset"
  >
    <div class="claim_issue">
      <el-alert
        title="实发只扣减实际库位库存；已按未领取量与实物可用量预填，可手动调整，支持部分实发。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form
        label-position="top"
        class="claim_issue__location"
      >
        <el-form-item
          label="实际出库库位"
          required
        >
          <ItemLocationSelect
            v-model="locationId"
            placeholder="选择本次实际实发的库位"
            :clearable="false"
            @change="loadContext"
          />
        </el-form-item>
      </el-form>

      <div v-loading="loading">
        <template v-if="context">
          <el-descriptions
            :column="3"
            border
            size="small"
            class="claim_issue__context"
          >
            <el-descriptions-item label="库位">
              {{ context.locationName }} · {{ context.locationCode }}
            </el-descriptions-item>
            <el-descriptions-item label="预约时段">
              {{ formatRange(context.bookingStartAt, context.bookingEndAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="最早实发">
              {{ formatTime(context.issueEarliestAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="领取方式">
              {{ pickupModeLabel(context.pickupMode) }}
            </el-descriptions-item>
            <el-descriptions-item label="实际领取人">
              {{ context.pickupUserName || context.pickupUserId || '—' }}
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-if="!context.canIssue"
            :title="context.issueBlockedReason || '当前不可实发'"
            type="warning"
            :closable="false"
            show-icon
            class="claim_issue__alert"
          />
          <el-alert
            v-for="warning in context.riskWarnings || []"
            :key="warning"
            :title="warning"
            type="warning"
            :closable="false"
            show-icon
            class="claim_issue__alert"
          />

          <el-table
            :data="forms"
            class="table-scroll-x claim_issue__table"
            row-key="claimLineId"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="claim_issue__expand">
                  <h4>有效预占（含其他库位）</h4>
                  <el-table
                    :data="row.reservations"
                    size="small"
                  >
                    <el-table-column
                      label="库位"
                      min-width="150"
                    >
                      <template #default="{ row: reservation }">
                        {{ reservation.locationName || reservation.locationId }} · {{ reservation.locationCode || '—' }}
                      </template>
                    </el-table-column>
                    <el-table-column
                      label="资产 / 规则"
                      min-width="140"
                    >
                      <template #default="{ row: reservation }">
                        {{ reservation.assetCode || reservation.reservationRule || '—' }}
                      </template>
                    </el-table-column>
                    <el-table-column
                      label="剩余预占"
                      width="110"
                      align="right"
                    >
                      <template #default="{ row: reservation }">
                        <ItemQuantityText
                          :value="reservation.remainingQuantity"
                          :unit="row.baseUnit"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column
                      label="预占时段"
                      min-width="210"
                    >
                      <template #default="{ row: reservation }">
                        {{ formatRange(reservation.reservationStartAt, reservation.reservationEndAt) }}
                      </template>
                    </el-table-column>
                  </el-table>
                  <template v-if="row.materialType === 'CONSUMABLE'">
                    <h4>实际库位实物</h4>
                    <el-table
                      :data="row.positions"
                      size="small"
                    >
                      <el-table-column
                        prop="positionText"
                        label="存放位置"
                        min-width="160"
                      >
                        <template #default="{ row: position }">
                          {{ position.positionText || '默认位置' }}
                        </template>
                      </el-table-column>
                      <el-table-column
                        label="保有"
                        width="120"
                        align="right"
                      >
                        <template #default="{ row: position }">
                          <ItemQuantityText
                            :value="position.ownedQuantity"
                            :unit="row.baseUnit"
                          />
                        </template>
                      </el-table-column>
                      <el-table-column
                        label="可用"
                        width="120"
                        align="right"
                      >
                        <template #default="{ row: position }">
                          <ItemQuantityText
                            :value="position.availableQuantity"
                            :unit="row.baseUnit"
                          />
                        </template>
                      </el-table-column>
                    </el-table>
                  </template>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="物料 / 未领取"
              min-width="210"
            >
              <template #default="{ row }">
                <div class="claim_issue__material">
                  <strong>{{ row.materialName }}</strong>
                  <span class="t-hint">{{ row.specification || row.materialId }}</span>
                  <span>未领取 <ItemQuantityText
                    :value="row.remainingQuantity"
                    :unit="row.baseUnit"
                  /></span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="预占剩余（本库位 / 全部）"
              min-width="170"
              align="right"
            >
              <template #default="{ row }">
                <ItemQuantityText
                  :value="reservedAtLocation(row)"
                  :unit="row.baseUnit"
                />
                <span class="t-hint"> / </span>
                <ItemQuantityText
                  :value="reservedTotal(row)"
                  :unit="row.baseUnit"
                />
              </template>
            </el-table-column>
            <el-table-column
              v-if="hasConsumable"
              label="存放位置"
              min-width="210"
            >
              <template #default="{ row }">
                <el-select
                  v-if="row.materialType === 'CONSUMABLE'"
                  v-model="row.positionText"
                  placeholder="选择实物存放位置"
                  class="claim_issue__control"
                >
                  <el-option
                    v-for="position in row.positions"
                    :key="position.positionText || '__DEFAULT__'"
                    :label="`${position.positionText || '默认位置'} · 可用 ${position.availableQuantity}`"
                    :value="position.positionText || ''"
                    :disabled="Number(position.availableQuantity) <= 0"
                  />
                </el-select>
                <span
                  v-else
                  class="t-hint"
                >固定资产按台实发</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="hasConsumable"
              label="本次实发数量"
              width="170"
            >
              <template #default="{ row }">
                <el-input-number
                  v-if="row.materialType === 'CONSUMABLE'"
                  v-model="row.quantity"
                  :min="0"
                  :max="Number(row.remainingQuantity)"
                  :precision="0"
                  :step="1"
                  controls-position="right"
                  class="claim_issue__control"
                />
                <span
                  v-else
                  class="t-hint"
                >—</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="hasAssets"
              label="逐台选择固定资产"
              min-width="280"
            >
              <template #default="{ row }">
                <el-select
                  v-if="row.materialType === 'FIXED_ASSET'"
                  v-model="row.fixedAssetIds"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="选择候选资产"
                  class="claim_issue__control"
                >
                  <el-option
                    v-for="asset in row.candidateAssets"
                    :key="asset.fixedAssetId"
                    :label="`${asset.assetCode} · ${asset.positionText || '默认位置'}${asset.reservedByThisClaim ? ' · 本申领预占' : ''}`"
                    :value="String(asset.fixedAssetId)"
                  />
                </el-select>
                <span
                  v-else
                  class="t-hint"
                >—</span>
              </template>
            </el-table-column>
          </el-table>
          <el-input
            v-model="remark"
            maxlength="512"
            show-word-limit
            placeholder="实发备注（可选）"
            class="claim_issue__remark"
          />
        </template>
        <el-empty
          v-else-if="!loading"
          description="请选择实际库位以加载未领取、预占和实物信息"
        />
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">
        取消
      </el-button>
      <el-button
        type="primary"
        :disabled="!context?.canIssue"
        :loading="submitting"
        @click="submit"
      >
        确认实发
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import { getClaimIssueContext, issueClaim } from '@/api/item/workflow'
import { formatInstant, formatRange } from '@/utils/datetime'
import { pickupModeLabel } from '@/views/item/components/claim.constants'
import { getConsumableIssueDefaults } from '../inventory.rules'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  claim: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const locationId = ref('')
const context = ref(null)
const forms = ref([])
const remark = ref('')
const loading = ref(false)
const submitting = ref(false)
let requestSerial = 0

const hasConsumable = computed(() => forms.value.some((line) => line.materialType === 'CONSUMABLE'))
const hasAssets = computed(() => forms.value.some((line) => line.materialType === 'FIXED_ASSET'))
const formatTime = (value) => formatInstant(value) || '—'
const reservedAtLocation = (line) => (line.reservations || [])
  .filter((item) => String(item.locationId) === String(locationId.value))
  .reduce((sum, item) => sum + Number(item.remainingQuantity || 0), 0)
const reservedTotal = (line) => (line.reservations || [])
  .reduce((sum, item) => sum + Number(item.remainingQuantity || 0), 0)

watch(visible, (value) => {
  if (value) reset()
})

function reset() {
  requestSerial += 1
  locationId.value = ''
  context.value = null
  forms.value = []
  remark.value = ''
  loading.value = false
  submitting.value = false
}

async function loadContext() {
  if (!locationId.value || !props.claim?.id) {
    context.value = null
    forms.value = []
    return
  }
  const serial = ++requestSerial
  loading.value = true
  try {
    const result = await getClaimIssueContext(props.claim.id, locationId.value)
    if (serial !== requestSerial) return
    context.value = result
    forms.value = (result?.lines || []).map((line) => {
      const defaults = line.materialType === 'CONSUMABLE'
        ? getConsumableIssueDefaults(line)
        : { positionText: null, quantity: 0 }
      return {
        ...line,
        claimLineId: String(line.claimLineId),
        positionText: defaults.positionText,
        quantity: defaults.quantity,
        fixedAssetIds: []
      }
    })
  } catch {
    if (serial === requestSerial) {
      context.value = null
      forms.value = []
    }
  } finally {
    if (serial === requestSerial) loading.value = false
  }
}

function validate(lines) {
  if (!lines.length) {
    ElMessage.warning('请至少填写一条实发行')
    return false
  }
  if (lines.some((line) => !Number.isInteger(Number(line.quantity)))) {
    ElMessage.warning('实发数量必须为整数')
    return false
  }
  for (const line of forms.value) {
    if (line.materialType === 'CONSUMABLE' && Number(line.quantity) > 0) {
      if (line.positionText === null || line.positionText === undefined) {
        ElMessage.warning(`请选择 ${line.materialName} 的实物 position`)
        return false
      }
      const position = line.positions.find((item) => (item.positionText || '') === (line.positionText || ''))
      if (!position || Number(line.quantity) > Number(position.availableQuantity)) {
        ElMessage.warning(`${line.materialName} 的实发数量超过该 position 可用量`)
        return false
      }
      if (Number(line.quantity) > Number(line.remainingQuantity)) {
        ElMessage.warning(`${line.materialName} 的实发数量超过未领取数量`)
        return false
      }
    }
    if (line.materialType === 'FIXED_ASSET' && line.fixedAssetIds.length > Number(line.remainingQuantity)) {
      ElMessage.warning(`${line.materialName} 选择资产数量超过未领取数量`)
      return false
    }
  }
  return true
}

async function submit() {
  const lines = []
  for (const line of forms.value) {
    if (line.materialType === 'CONSUMABLE' && Number(line.quantity) > 0) {
      lines.push({
        claimLineId: String(line.claimLineId),
        quantity: line.quantity,
        positionText: line.positionText || ''
      })
    }
    if (line.materialType === 'FIXED_ASSET') {
      line.fixedAssetIds.forEach((assetId) => {
        const asset = line.candidateAssets.find((item) => String(item.fixedAssetId) === String(assetId))
        lines.push({
          claimLineId: String(line.claimLineId),
          fixedAssetId: String(assetId),
          quantity: 1,
          positionText: asset?.positionText || ''
        })
      })
    }
  }
  if (!validate(lines)) return
  const totalRemaining = forms.value.reduce((sum, line) => sum + Number(line.remainingQuantity || 0), 0)
  const issueQuantity = lines.reduce((sum, line) => sum + Number(line.quantity || 0), 0)
  submitting.value = true
  try {
    const result = await issueClaim(props.claim.id, {
      locationId: String(locationId.value),
      idempotencyKey: `claim-issue-${props.claim.id}-${crypto.randomUUID()}`,
      remark: remark.value || undefined,
      lines
    })
    ElMessage.success(issueQuantity < totalRemaining
      ? '部分实发成功，可在其他有预占和实物的库位继续实发剩余数量'
      : '实发成功')
    visible.value = false
    emit('success', result)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.claim_issue__location { margin-top: 18px; max-width: 460px; }
.claim_issue__context { margin-bottom: 12px; }
.claim_issue__alert { margin-bottom: 10px; }
.claim_issue__table { margin-top: 16px; }
.claim_issue__expand { padding: 8px 20px; }
.claim_issue__expand h4 { margin: 8px 0; }
.claim_issue__material { display: flex; flex-direction: column; gap: 4px; }
.claim_issue__control { width: 100%; }
.claim_issue__remark { margin-top: 16px; }
</style>
