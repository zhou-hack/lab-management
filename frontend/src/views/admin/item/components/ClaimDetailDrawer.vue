<template>
  <el-drawer
    v-model="visible"
    title="申领详情"
    size="min(1120px, 94vw)"
    destroy-on-close
  >
    <div
      v-loading="loading"
      class="claim_detail"
    >
      <template v-if="claim">
        <div class="card claim_detail__summary">
          <div class="claim_detail__title">
            <div>
              <h3 class="t-title">
                {{ claim.documentNo }}
              </h3>
              <p class="t-sub">
                {{ claim.applicantName || claim.applicantId }} · {{ claim.purpose || '未填写用途' }}
              </p>
            </div>
            <ItemStatusPill :status="claim.status" />
          </div>
          <el-descriptions
            :column="3"
            border
            size="small"
            class="claim_detail__descriptions"
          >
            <el-descriptions-item label="申领单ID">
              {{ claim.id }}
            </el-descriptions-item>
            <el-descriptions-item label="共享单据ID">
              {{ claim.bizDocumentId }}
            </el-descriptions-item>
            <el-descriptions-item label="业务链ID">
              {{ claim.businessChainId }}
            </el-descriptions-item>
            <el-descriptions-item label="预约日期">
              {{ claim.bookingDate || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="预约时段">
              {{ formatRange(claim.bookingStartAt, claim.bookingEndAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="当前版本">
              {{ claim.revisionNo }}
            </el-descriptions-item>
            <el-descriptions-item label="领取方式">
              {{ pickupModeLabel(claim.pickupMode) }}
            </el-descriptions-item>
            <el-descriptions-item label="实际领取人">
              {{ claim.pickupUserName || claim.pickupUserId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="claim.pickupMode === 'ASSISTANT'"
              label="代领学生"
            >
              {{ claim.delegateStudentName || claim.delegateStudentId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="归还期限">
              {{ formatTime(claim.returnDeadline) }}
            </el-descriptions-item>
            <el-descriptions-item label="最早实发">
              {{ formatTime(claim.issueEarliestAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatTime(claim.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="claim.rejectReason"
              label="驳回原因"
              :span="3"
            >
              {{ claim.rejectReason }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="claim.abandonReason"
              label="废弃申请"
              :span="3"
            >
              {{ claim.abandonReason }}（{{ formatTime(claim.abandonRequestedAt) }}）
            </el-descriptions-item>
            <el-descriptions-item
              v-if="claim.closeReason"
              label="关闭原因"
              :span="3"
            >
              {{ claim.closeReason }}
            </el-descriptions-item>
          </el-descriptions>
          <div class="claim_detail__actions">
            <el-button
              v-if="claim.canReview"
              v-permission="'item:claim:review'"
              type="primary"
              @click="emit('action', 'approve', claim)"
            >
              审核通过
            </el-button>
            <el-button
              v-if="claim.canReview"
              v-permission="'item:claim:review'"
              type="danger"
              plain
              @click="emit('action', 'reject', claim)"
            >
              驳回
            </el-button>
            <el-button
              v-if="claim.canIssue"
              v-permission="'item:claim:issue'"
              type="success"
              @click="emit('action', 'issue', claim)"
            >
              实发
            </el-button>
            <el-button
              v-if="claim.canClose"
              v-permission="['item:claim:abandon', 'item:claim:review']"
              type="warning"
              plain
              @click="emit('action', 'close', claim)"
            >
              关闭申领
            </el-button>
            <el-button @click="emit('action', 'documents', claim)">
              业务链单据
            </el-button>
          </div>
        </div>

        <el-tabs class="claim_detail__tabs">
          <el-tab-pane
            label="当前申领行"
            name="lines"
          >
            <el-table
              :data="claim.lines || []"
              class="table-scroll-x"
            >
              <el-table-column
                label="物料"
                min-width="190"
              >
                <template #default="{ row }">
                  <div class="claim_detail__material">
                    <span>{{ row.materialName }}</span>
                    <span class="t-hint">{{ row.specification || row.materialId }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="类型"
                width="100"
              >
                <template #default="{ row }">
                  {{ row.materialType === 'FIXED_ASSET' ? '固定资产' : '耗材' }}
                </template>
              </el-table-column>
              <el-table-column
                label="申请"
                width="110"
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
                label="已实发"
                width="110"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.issuedQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="未领取"
                width="110"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.unclaimedQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                prop="id"
                label="申领行ID"
                min-width="180"
              />
            </el-table>
          </el-tab-pane>
          <el-tab-pane
            label="计划分配"
            name="allocations"
          >
            <el-table
              :data="claim.allocations || []"
              class="table-scroll-x"
            >
              <el-table-column
                prop="materialId"
                label="物料ID"
                min-width="160"
              />
              <el-table-column
                label="计划库位"
                min-width="180"
              >
                <template #default="{ row }">
                  {{ row.locationName || row.locationId }} · {{ row.locationCode || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="计划数量"
                width="120"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText :value="row.plannedQuantity" />
                </template>
              </el-table-column>
              <el-table-column
                prop="claimLineId"
                label="申领行ID"
                min-width="170"
              />
              <el-table-column
                prop="revisionNo"
                label="版本"
                width="70"
              />
            </el-table>
          </el-tab-pane>
          <el-tab-pane
            label="预占"
            name="reservations"
          >
            <el-table
              :data="claim.reservations || []"
              class="table-scroll-x"
            >
              <el-table-column
                label="库位"
                min-width="170"
              >
                <template #default="{ row }">
                  {{ row.locationName || row.locationId }} · {{ row.locationCode || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="资产 / 规则"
                min-width="160"
              >
                <template #default="{ row }">
                  {{ row.assetCode || row.reservationRule || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="预占"
                width="100"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText :value="row.reservedQuantity" />
                </template>
              </el-table-column>
              <el-table-column
                label="已消耗"
                width="100"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText :value="row.consumedQuantity" />
                </template>
              </el-table-column>
              <el-table-column
                label="剩余"
                width="100"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText :value="row.remainingQuantity" />
                </template>
              </el-table-column>
              <el-table-column
                label="状态"
                width="100"
              >
                <template #default="{ row }">
                  <ItemStatusPill :status="row.status" />
                </template>
              </el-table-column>
              <el-table-column
                label="预占时段"
                min-width="220"
              >
                <template #default="{ row }">
                  {{ formatRange(row.reservationStartAt, row.reservationEndAt) }}
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane
            label="事件"
            name="events"
          >
            <el-timeline v-if="claim.events?.length">
              <el-timeline-item
                v-for="event in claim.events"
                :key="event.id"
                :timestamp="formatTime(event.occurredAt)"
                placement="top"
              >
                <div class="claim_detail__event">
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
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty
              v-else
              description="暂无事件"
            />
          </el-tab-pane>
          <el-tab-pane
            label="可用操作"
            name="actions"
          >
            <el-descriptions
              :column="2"
              border
            >
              <el-descriptions-item
                v-for="item in actionItems"
                :key="item.key"
                :label="item.label"
              >
                <span :class="item.allowed ? 'claim_detail__allowed' : 'claim_detail__blocked'">
                  {{ item.allowed ? '允许' : '不可操作' }}
                </span>
                <span
                  v-if="!item.allowed && claim.actionReasons?.[item.reasonKey]"
                  class="t-hint"
                >
                  · {{ claim.actionReasons[item.reasonKey] }}
                </span>
              </el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import { formatInstant, formatRange } from '@/utils/datetime'
import { eventTypeLabel, pickupModeLabel } from '@/views/item/components/claim.constants'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  claim: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'action'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const actionItems = computed(() => {
  const claim = props.claim || {}
  return [
    { key: 'canReview', reasonKey: 'review', label: '审核 / 驳回', allowed: claim.canReview },
    { key: 'canModify', reasonKey: 'modify', label: '修改', allowed: claim.canModify },
    { key: 'canCancel', reasonKey: 'cancel', label: '取消', allowed: claim.canCancel },
    { key: 'canAbandon', reasonKey: 'abandon', label: '申请废弃', allowed: claim.canAbandon },
    { key: 'canClose', reasonKey: 'close', label: '关闭', allowed: claim.canClose },
    { key: 'canRebook', reasonKey: 'rebook', label: '再次生成', allowed: claim.canRebook },
    { key: 'canIssue', reasonKey: 'issue', label: '实发', allowed: claim.canIssue }
  ]
})
const formatTime = (value) => formatInstant(value) || '—'
</script>

<style scoped>
.claim_detail__summary { margin-bottom: 16px; }
.claim_detail__title { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.claim_detail__title h3 { margin: 0 0 6px; }
.claim_detail__title p { margin: 0; }
.claim_detail__descriptions { margin-bottom: 16px; }
.claim_detail__actions { display: flex; flex-wrap: wrap; gap: 8px; }
.claim_detail__material { display: flex; flex-direction: column; gap: 3px; }
.claim_detail__event { display: flex; flex-wrap: wrap; gap: 8px 14px; }
.claim_detail__event p { flex-basis: 100%; margin: 3px 0 0; }
.claim_detail__allowed { color: var(--ok); }
.claim_detail__blocked { color: var(--muted); }
@media (max-width: 768px) {
  .claim_detail__descriptions :deep(.el-descriptions__body) { overflow-x: auto; }
}
</style>
