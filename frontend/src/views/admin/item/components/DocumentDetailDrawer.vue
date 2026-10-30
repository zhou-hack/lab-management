<template>
  <el-drawer
    v-model="visible"
    title="库存单据详情"
    size="min(1100px, 94vw)"
    destroy-on-close
  >
    <div
      v-loading="loading"
      class="document_detail"
    >
      <template v-if="detail?.document">
        <div class="card">
          <div class="document_detail__head">
            <div>
              <h3 class="t-title">
                {{ detail.document.documentNo }}
              </h3>
              <p class="t-sub">
                {{ documentTypeLabel(detail.document.documentType) }} · {{ businessSceneLabel(detail.document.businessScene) }}
              </p>
            </div>
            <ItemStatusPill :status="detail.document.reviewStatus || detail.document.transferStatus || detail.document.processStatus || 'POSTED'" />
          </div>
          <el-descriptions
            :column="3"
            border
            size="small"
          >
            <el-descriptions-item label="库存单据ID">
              {{ detail.document.id }}
            </el-descriptions-item>
            <el-descriptions-item label="共享单据ID">
              {{ detail.document.bizDocumentId }}
            </el-descriptions-item>
            <el-descriptions-item label="业务链ID">
              {{ detail.document.businessChainId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="来源库存单ID">
              {{ detail.document.sourceMaterialDocumentId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="库位">
              {{ detail.document.locationName || detail.document.locationId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="序号">
              {{ detail.document.documentSeq ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="源 / 目标库位ID">
              {{ detail.document.fromLocationId || '—' }} / {{ detail.document.toLocationId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="经办人">
              {{ detail.document.operatorName || detail.document.operatorId }}
            </el-descriptions-item>
            <el-descriptions-item label="实际领取人">
              {{ detail.document.pickupUserName || detail.document.pickupUserId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="发生时间">
              {{ formatTime(detail.document.occurredAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="过账时间">
              {{ formatTime(detail.document.postedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="处理状态">
              <ItemStatusPill
                v-if="detail.document.processStatus"
                :status="detail.document.processStatus"
              />
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="归还状态">
              <ItemStatusPill
                v-if="detail.document.returnStatus"
                :status="detail.document.returnStatus"
              />
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="复核状态">
              <ItemStatusPill
                v-if="detail.document.reviewStatus"
                :status="detail.document.reviewStatus"
              />
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="复核人">
              {{ detail.document.reviewedBy || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="版本">
              {{ detail.document.version }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="detail.document.reviewReason"
              label="复核意见"
              :span="3"
            >
              {{ detail.document.reviewReason }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="detail.document.overdueReason"
              label="逾期原因"
              :span="3"
            >
              {{ detail.document.overdueReason }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="detail.document.remark"
              label="备注"
              :span="3"
            >
              {{ detail.document.remark }}
            </el-descriptions-item>
          </el-descriptions>
          <div
            v-if="canReturn"
            class="document_detail__actions"
          >
            <el-button
              v-permission="'item:claim:return'"
              type="primary"
              @click="emit('return', detail)"
            >
              登记归还 / 出库核销
            </el-button>
          </div>
        </div>

        <el-tabs class="document_detail__tabs">
          <el-tab-pane
            label="单据行"
            name="lines"
          >
            <el-table
              :data="detail.lines || []"
              class="table-scroll-x"
            >
              <el-table-column
                label="物料 / 资产"
                min-width="210"
              >
                <template #default="{ row }">
                  <div class="document_detail__material">
                    <strong>{{ row.materialName }}</strong>
                    <span class="t-hint">{{ row.assetCode || row.specification || row.materialId }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="数量"
                width="120"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.quantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="存放位置"
                min-width="140"
              >
                <template #default="{ row }">
                  {{ row.positionText || '默认位置' }}
                </template>
              </el-table-column>
              <el-table-column
                prop="id"
                label="单据行ID"
                min-width="180"
              />
              <el-table-column
                prop="sourceMaterialDocumentLineId"
                label="来源行ID"
                min-width="180"
              />
              <el-table-column
                prop="claimLineId"
                label="申领行ID"
                min-width="180"
              />
            </el-table>
          </el-tab-pane>
          <el-tab-pane
            label="核销明细"
            name="settlements"
          >
            <el-table
              :data="detail.settlements || []"
              class="table-scroll-x"
            >
              <el-table-column
                prop="materialName"
                label="物料"
                min-width="170"
              />
              <el-table-column
                label="资产"
                min-width="130"
              >
                <template #default="{ row }">
                  {{ row.assetCode || row.fixedAssetId || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="类型"
                width="120"
              >
                <template #default="{ row }">
                  {{ settlementTypeLabel(row.settlementType) }}
                </template>
              </el-table-column>
              <el-table-column
                label="数量"
                width="100"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText :value="row.quantity" />
                </template>
              </el-table-column>
              <el-table-column
                label="目标状态"
                width="120"
              >
                <template #default="{ row }">
                  <ItemStatusPill
                    v-if="row.targetAssetStatus"
                    :status="row.targetAssetStatus"
                  /><span v-else>—</span>
                </template>
              </el-table-column>
              <el-table-column
                prop="reason"
                label="原因"
                min-width="220"
                show-overflow-tooltip
              />
              <el-table-column
                prop="sourceMaterialDocumentLineId"
                label="来源行ID"
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
                <div class="document_detail__event">
                  <strong>{{ stockEventTypeLabel(event.eventType, detail.document.businessScene) }}</strong>
                  <span>{{ event.actorName || event.actorId }}</span>
                  <span
                    v-if="event.fromStatus || event.toStatus"
                    class="document_detail__event_status"
                  >
                    <ItemStatusPill
                      v-if="event.fromStatus"
                      :status="event.fromStatus"
                    />
                    <span v-else>—</span>
                    <span>→</span>
                    <ItemStatusPill
                      v-if="event.toStatus"
                      :status="event.toStatus"
                    />
                    <span v-else>—</span>
                  </span>
                  <p
                    v-if="event.reason"
                    class="t-sub"
                  >
                    {{ event.reason }}
                  </p>
                  <p class="t-hint">
                    事件单据ID {{ event.documentId }}
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
        description="未获取到单据详情"
      />
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import { getDocument } from '@/api/item/workflow'
import { formatInstant } from '@/utils/datetime'
import {
  businessSceneLabel,
  documentTypeLabel,
  settlementTypeLabel,
  stockEventTypeLabel
} from '@/views/item/components/claim.constants'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  documentId: { type: [String, Number], default: '' }
})
const emit = defineEmits(['update:modelValue', 'return', 'loaded'])
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const detail = ref(null)
const loading = ref(false)
let requestSerial = 0

const canReturn = computed(() => {
  const document = detail.value?.document
  return document?.documentType === 'OUTBOUND'
    && document?.businessScene === 'CLAIM_ISSUE'
    && document?.processStatus !== 'PROCESSED'
})
const formatTime = (value) => formatInstant(value) || '—'

watch(visible, (value) => {
  if (value) reload()
  else {
    requestSerial += 1
    detail.value = null
  }
})

async function reload() {
  if (!props.documentId) return
  const serial = ++requestSerial
  loading.value = true
  try {
    const result = await getDocument(props.documentId)
    if (serial !== requestSerial) return
    detail.value = result
    emit('loaded', result)
  } finally {
    if (serial === requestSerial) loading.value = false
  }
}

defineExpose({ reload, detail })
</script>

<style scoped>
.document_detail__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.document_detail__head h3 { margin: 0 0 6px; }
.document_detail__head p { margin: 0; }
.document_detail__actions { margin-top: 16px; }
.document_detail__tabs { margin-top: 16px; }
.document_detail__material { display: flex; flex-direction: column; gap: 3px; }
.document_detail__event { display: flex; flex-wrap: wrap; gap: 7px 14px; }
.document_detail__event_status { display: inline-flex; align-items: center; gap: 6px; }
.document_detail__event p { flex-basis: 100%; margin: 0; }
</style>
