<template>
  <el-drawer
    v-model="visible"
    title="移动单详情"
    size="min(1120px, 95vw)"
    destroy-on-close
  >
    <div
      v-loading="loading"
      class="transfer_detail"
    >
      <template v-if="detail?.document">
        <div class="card">
          <div class="transfer_detail__head">
            <div>
              <h3 class="t-title">
                {{ detail.document.documentNo }}
              </h3>
              <p class="t-sub">
                {{ detail.document.fromLocationName || detail.document.fromLocationId }} → {{ detail.document.toLocationName || detail.document.toLocationId }}
              </p>
            </div>
            <ItemStatusPill :status="detail.document.transferStatus" />
          </div>
          <el-descriptions
            :column="3"
            border
            size="small"
          >
            <el-descriptions-item label="移动单ID">
              {{ detail.document.id }}
            </el-descriptions-item>
            <el-descriptions-item label="共享单据ID">
              {{ detail.document.bizDocumentId }}
            </el-descriptions-item>
            <el-descriptions-item label="版本">
              {{ detail.document.version }}
            </el-descriptions-item>
            <el-descriptions-item label="源库位">
              {{ detail.document.fromLocationName || detail.document.fromLocationId }}
            </el-descriptions-item>
            <el-descriptions-item label="目标库位">
              {{ detail.document.toLocationName || detail.document.toLocationId }}
            </el-descriptions-item>
            <el-descriptions-item label="原经办人">
              {{ detail.document.operatorName || detail.document.operatorId }}
            </el-descriptions-item>
            <el-descriptions-item label="发出时间">
              {{ formatTime(detail.document.occurredAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="最近接收人">
              {{ detail.document.receivedBy || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="最近接收时间">
              {{ formatTime(detail.document.receivedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="总剩余在途">
              <ItemQuantityText :value="detail.remainingQuantity" />
            </el-descriptions-item>
            <el-descriptions-item
              v-if="detail.document.remark"
              label="备注"
              :span="3"
            >
              {{ detail.document.remark }}
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            title="移动发出仅形成在途数量；目标管理员确认实收前，目标库位不会增加库存。"
            type="info"
            :closable="false"
            show-icon
            class="transfer_detail__notice"
          />
          <div class="transfer_detail__actions">
            <el-button
              v-if="detail.canReceive"
              v-permission="'item:transfer:receive'"
              type="primary"
              @click="openReceipt('receive')"
            >
              目标实收
            </el-button>
            <el-button
              v-if="detail.canReturnRequest"
              v-permission="'item:transfer:receive'"
              type="danger"
              plain
              @click="actionType = 'returnRequest'"
            >
              拒收 / 请求退回
            </el-button>
            <el-button
              v-if="detail.canReturnReceive"
              v-permission="'item:transfer:receive'"
              type="warning"
              @click="openReceipt('returnReceive')"
            >
              源库位退回实收
            </el-button>
            <el-button
              v-if="detail.canWriteoff"
              v-permission="'item:settlement:create'"
              type="warning"
              plain
              @click="openWriteoff"
            >
              在途核销
            </el-button>
            <el-button
              v-if="detail.canReview"
              v-permission="'item:settlement:review'"
              type="success"
              :disabled="!pendingSettlementId"
              @click="emit('review', pendingSettlementId)"
            >
              复核在途核销
            </el-button>
          </div>
        </div>

        <el-tabs class="transfer_detail__tabs">
          <el-tab-pane
            label="逐行处理"
            name="lines"
          >
            <el-table
              :data="detail.lines || []"
              class="table-scroll-x"
            >
              <el-table-column
                label="物料 / 资产"
                min-width="200"
              >
                <template #default="{ row }">
                  <div class="transfer_detail__material">
                    <strong>{{ row.materialName }}</strong>
                    <span class="t-hint">{{ row.assetCode || row.specification || row.materialId }}</span>
                    <span class="t-hint">{{ row.positionText || '默认位置' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="发出"
                width="100"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.sentQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="实收"
                width="100"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.receivedQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="退回"
                width="100"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.returnedQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="核销"
                width="100"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.settledQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="剩余"
                width="100"
                align="right"
              >
                <template #default="{ row }">
                  <strong><ItemQuantityText
                    :value="row.remainingQuantity"
                    :unit="row.baseUnit"
                  /></strong>
                </template>
              </el-table-column>
              <el-table-column
                prop="sourceMaterialDocumentLineId"
                label="原移动行ID"
                min-width="180"
              />
            </el-table>
          </el-tab-pane>
          <el-tab-pane
            label="事件"
            name="events"
          >
            <el-timeline v-if="detail.events?.length">
              <el-timeline-item
                v-for="event in detail.events"
                :key="event.id"
                :timestamp="formatTime(event.occurredAt)"
                placement="top"
              >
                <div class="transfer_detail__event">
                  <strong>{{ eventTypeLabel(event.eventType) }}</strong>
                  <span>{{ event.actorName || event.actorId }}</span>
                  <span v-if="event.fromStatus || event.toStatus">
                    <ItemStatusPill
                      v-if="event.fromStatus"
                      :status="event.fromStatus"
                    /><span v-else>—</span>
                    →
                    <ItemStatusPill
                      v-if="event.toStatus"
                      :status="event.toStatus"
                    /><span v-else>—</span>
                  </span>
                  <p
                    v-if="event.reason"
                    class="t-sub"
                  >
                    {{ event.reason }}
                  </p>
                  <p
                    v-if="event.requestKey"
                    class="t-hint"
                  >
                    {{ event.requestKey }}
                  </p>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty
              v-else
              description="暂无事件"
            />
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty
        v-else-if="!loading"
        description="未获取到移动单"
      />
    </div>

    <el-dialog
      v-model="receiptVisible"
      :title="actionType === 'returnReceive' ? '源库位退回实收' : '目标库位实收'"
      width="min(960px, 94vw)"
      append-to-body
    >
      <el-alert
        v-if="hasShortReceipt"
        title="本次存在短收：未填写的剩余在途数量不会被接收，仍可继续退回或由源管理员核销。"
        type="warning"
        :closable="false"
        show-icon
        class="transfer_detail__dialog_alert"
      />
      <el-table
        :data="actionLines"
        class="table-scroll-x"
      >
        <el-table-column
          label="物料 / 资产"
          min-width="190"
        >
          <template #default="{ row }">
            {{ row.materialName }}<span class="t-hint transfer_detail__block"> {{ row.assetCode || row.specification }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="剩余在途"
          width="120"
          align="right"
        >
          <template #default="{ row }">
            <ItemQuantityText
              :value="row.remainingQuantity"
              :unit="row.baseUnit"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="本次实收"
          width="170"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="0"
              :max="Number(row.remainingQuantity)"
              :precision="0"
              :step="1"
              controls-position="right"
              class="transfer_detail__number"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="入库存放位置"
          min-width="190"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.positionText"
              maxlength="256"
              placeholder="默认为原移动存放位置"
            />
          </template>
        </el-table-column>
      </el-table>
      <el-input
        v-model="remark"
        maxlength="512"
        show-word-limit
        placeholder="实收备注（可选）"
        class="transfer_detail__remark"
      />
      <template #footer>
        <el-button @click="receiptVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitReceipt"
        >
          确认实收
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      :model-value="actionType === 'returnRequest'"
      title="拒收并请求退回"
      width="520px"
      append-to-body
      @update:model-value="(value) => !value && (actionType = '')"
    >
      <el-input
        v-model="returnReason"
        type="textarea"
        maxlength="512"
        show-word-limit
        placeholder="必填：说明拒收原因"
      />
      <template #footer>
        <el-button @click="actionType = ''">
          取消
        </el-button>
        <el-button
          type="danger"
          :loading="submitting"
          @click="submitReturnRequest"
        >
          确认拒收
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="writeoffVisible"
      title="在途核销登记"
      width="min(1000px, 95vw)"
      append-to-body
    >
      <el-alert
        title="在途核销由原移动经办人发起，提交后进入 TRANSFER_SETTLEMENT 待复核；复核通过才完成核销。"
        type="info"
        :closable="false"
        show-icon
        class="transfer_detail__dialog_alert"
      />
      <el-table
        :data="actionLines"
        class="table-scroll-x"
      >
        <el-table-column
          prop="materialName"
          label="物料"
          min-width="170"
        />
        <el-table-column
          label="剩余"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            <ItemQuantityText
              :value="row.remainingQuantity"
              :unit="row.baseUnit"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="核销类型"
          width="140"
        >
          <template #default="{ row }">
            <el-select v-model="row.settlementType">
              <el-option
                label="丢失"
                value="LOST"
              /><el-option
                label="报废"
                value="SCRAP"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column
          label="核销数量"
          width="160"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="0"
              :max="Number(row.remainingQuantity)"
              :precision="0"
              :step="1"
              controls-position="right"
              class="transfer_detail__number"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="核销原因"
          min-width="220"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.reason"
              maxlength="512"
              placeholder="数量大于0时必填"
            />
          </template>
        </el-table-column>
      </el-table>
      <el-input
        v-model="remark"
        maxlength="512"
        show-word-limit
        placeholder="核销单备注（可选）"
        class="transfer_detail__remark"
      />
      <template #footer>
        <el-button @click="writeoffVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitWriteoff"
        >
          提交待复核
        </el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import {
  getTransfer,
  receiveTransfer,
  receiveTransferReturn,
  requestTransferReturn,
  writeoffTransfer
} from '@/api/item/workflow'
import { formatInstant } from '@/utils/datetime'
import { eventTypeLabel } from '@/views/item/components/claim.constants'
import { getTransferReceiptError } from '../inventory.rules'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  transferId: { type: [String, Number], default: '' }
})
const emit = defineEmits(['update:modelValue', 'changed', 'review'])
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const detail = ref(null)
const loading = ref(false)
const submitting = ref(false)
const actionType = ref('')
const actionLines = ref([])
const receiptVisible = ref(false)
const writeoffVisible = ref(false)
const returnReason = ref('')
const remark = ref('')
let requestSerial = 0

const formatTime = (value) => formatInstant(value) || '—'
const hasShortReceipt = computed(() => actionLines.value.some((line) => Number(line.quantity || 0) < Number(line.remainingQuantity || 0)))
const pendingSettlementId = computed(() => {
  const reviewed = new Set()
  const pending = []
  for (const event of detail.value?.events || []) {
    let match = /^SETTLEMENT_REVIEW:(\d+)$/.exec(event.requestKey || '')
    if (match) reviewed.add(match[1])
    match = /^SETTLEMENT_SUBMIT:(\d+)$/.exec(event.requestKey || '')
    if (match && !pending.includes(match[1])) pending.push(match[1])
  }
  return pending.filter((id) => !reviewed.has(id)).at(-1) || ''
})

watch(visible, (value) => {
  if (value) reload()
  else {
    requestSerial += 1
    detail.value = null
    closeDialogs()
  }
})

function closeDialogs() {
  actionType.value = ''
  receiptVisible.value = false
  writeoffVisible.value = false
  actionLines.value = []
  returnReason.value = ''
  remark.value = ''
}

async function reload() {
  if (!props.transferId) return
  const serial = ++requestSerial
  loading.value = true
  try {
    const result = await getTransfer(props.transferId)
    if (serial === requestSerial) detail.value = result
  } finally {
    if (serial === requestSerial) loading.value = false
  }
}

function openReceipt(type) {
  closeDialogs()
  actionType.value = type
  actionLines.value = (detail.value?.lines || []).filter((line) => Number(line.remainingQuantity) > 0).map((line) => ({
    ...line,
    sourceMaterialDocumentLineId: String(line.sourceMaterialDocumentLineId),
    quantity: Number(line.remainingQuantity),
    positionText: line.positionText || ''
  }))
  receiptVisible.value = true
}

function openWriteoff() {
  closeDialogs()
  actionLines.value = (detail.value?.lines || []).filter((line) => Number(line.remainingQuantity) > 0).map((line) => ({
    ...line,
    sourceMaterialDocumentLineId: String(line.sourceMaterialDocumentLineId),
    quantity: line.fixedAssetId ? 1 : 0,
    settlementType: 'LOST',
    reason: ''
  }))
  writeoffVisible.value = true
}

async function submitReceipt() {
  const error = getTransferReceiptError(actionLines.value)
  if (error) {
    ElMessage.warning(error)
    return
  }
  const lines = actionLines.value.filter((line) => Number(line.quantity) > 0).map((line) => ({
    sourceMaterialDocumentLineId: String(line.sourceMaterialDocumentLineId),
    quantity: line.quantity,
    positionText: line.positionText || ''
  }))
  if (hasShortReceipt.value) {
    try {
      await ElMessageBox.confirm('本次存在短收，剩余数量仍保留在途。确认继续？', '短收确认', { type: 'warning' })
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    const api = actionType.value === 'returnReceive' ? receiveTransferReturn : receiveTransfer
    await api(props.transferId, {
      lines,
      idempotencyKey: `transfer-${actionType.value}-${props.transferId}-${crypto.randomUUID()}`,
      remark: remark.value || undefined
    })
    ElMessage.success(actionType.value === 'returnReceive' ? '退回实收成功' : '目标实收成功，目标库存已更新')
    receiptVisible.value = false
    await reload()
    emit('changed')
  } finally {
    submitting.value = false
  }
}

async function submitReturnRequest() {
  if (!returnReason.value.trim()) {
    ElMessage.warning('拒收原因不能为空')
    return
  }
  submitting.value = true
  try {
    await requestTransferReturn(props.transferId, {
      reason: returnReason.value.trim(),
      idempotencyKey: `transfer-return-request-${props.transferId}-${crypto.randomUUID()}`
    })
    ElMessage.success('已拒收并请求源库位退回')
    actionType.value = ''
    await reload()
    emit('changed')
  } finally {
    submitting.value = false
  }
}

async function submitWriteoff() {
  const lines = actionLines.value.filter((line) => Number(line.quantity) > 0).map((line) => ({
    sourceMaterialDocumentLineId: String(line.sourceMaterialDocumentLineId),
    settlementType: line.settlementType,
    quantity: line.quantity,
    reason: line.reason.trim()
  }))
  if (!lines.length) {
    ElMessage.warning('请至少填写一条核销行')
    return
  }
  if (lines.some((line) => !Number.isInteger(Number(line.quantity)))) {
    ElMessage.warning('核销数量必须为整数')
    return
  }
  if (lines.some((line) => !line.reason)) {
    ElMessage.warning('每条在途核销行都必须填写原因')
    return
  }
  submitting.value = true
  try {
    await writeoffTransfer(props.transferId, {
      lines,
      idempotencyKey: `transfer-writeoff-${props.transferId}-${crypto.randomUUID()}`,
      remark: remark.value || undefined
    })
    ElMessage.success('在途核销已提交，请由其他管理员复核')
    writeoffVisible.value = false
    await reload()
    emit('changed')
  } finally {
    submitting.value = false
  }
}

defineExpose({ reload, detail })
</script>

<style scoped>
.transfer_detail__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.transfer_detail__head h3 { margin: 0 0 6px; }
.transfer_detail__head p { margin: 0; }
.transfer_detail__notice { margin-top: 14px; }
.transfer_detail__actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.transfer_detail__tabs { margin-top: 16px; }
.transfer_detail__material { display: flex; flex-direction: column; gap: 3px; }
.transfer_detail__event { display: flex; flex-wrap: wrap; gap: 7px 14px; }
.transfer_detail__event p { flex-basis: 100%; margin: 0; overflow-wrap: anywhere; }
.transfer_detail__dialog_alert { margin-bottom: 12px; }
.transfer_detail__number { width: 145px; }
.transfer_detail__remark { margin-top: 14px; }
.transfer_detail__block { display: block; }
</style>
