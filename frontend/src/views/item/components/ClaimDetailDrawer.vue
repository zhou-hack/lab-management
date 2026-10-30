<template>
  <el-drawer
    :model-value="modelValue"
    :size="drawerSize"
    class="claim_detail_drawer"
    :before-close="beforeClose"
    @update:model-value="(visible) => !visible && emit('close')"
  >
    <template #header>
      <div class="claim_detail__header">
        <div>
          <h3 class="t-title">
            {{ detail?.documentNo || '申领详情' }}
          </h3>
          <p
            v-if="detail"
            class="t-sub"
          >
            申领 ID：{{ detail.id }} · 版本 {{ detail.revisionNo }}
          </p>
        </div>
        <ItemStatusPill
          v-if="detail"
          :status="detail.status"
        />
      </div>
    </template>

    <div
      v-loading="loading"
      class="claim_detail"
    >
      <template v-if="detail">
        <el-alert
          v-if="detail.status === 'REJECTED'"
          type="error"
          :closable="false"
          show-icon
          title="申领已驳回"
        >
          <div class="claim_detail__reason">
            {{ detail.rejectReason || '未填写驳回原因' }}
          </div>
        </el-alert>
        <el-alert
          v-if="detail.status === 'CANCELLED'"
          type="info"
          :closable="false"
          show-icon
          title="申领已取消"
        >
          <div class="claim_detail__reason">
            取消原因：{{ cancelReason || '未填写' }}
          </div>
        </el-alert>
        <el-alert
          v-if="detail.status === 'CLOSED'"
          type="info"
          :closable="false"
          show-icon
          title="申领已关闭"
        >
          <div class="claim_detail__reason">
            关闭原因：{{ closeReasonText }}
          </div>
        </el-alert>
        <el-alert
          v-if="detail.abandonRequestedAt && detail.status !== 'CLOSED'"
          type="warning"
          :closable="false"
          show-icon
          title="已申请放弃剩余"
        >
          <div class="claim_detail__reason">
            {{ detail.abandonReason || '未填写原因' }} · {{ formatInstant(detail.abandonRequestedAt) }}
          </div>
        </el-alert>
        <el-alert
          v-if="detail.status === 'PARTIALLY_ISSUED'"
          type="warning"
          :closable="false"
          show-icon
          title="部分实发后的操作限制"
        >
          已产生实发记录，不能修改或取消整张申领；如不再领取余量，请申请放弃剩余。
        </el-alert>

        <section class="card claim_detail__section">
          <h4 class="claim_detail__section_title">
            申请信息
          </h4>
          <el-descriptions
            :column="2"
            border
          >
            <el-descriptions-item
              label="用途"
              :span="2"
            >
              {{ detail.purpose || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="预约日期">
              {{ detail.bookingDate || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="预约时间">
              {{ formatRange(detail.bookingStartAt, detail.bookingEndAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="起止节次 ID">
              {{ detail.startPeriodId || '—' }} ~ {{ detail.endPeriodId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="归还期限">
              {{ formatInstant(detail.returnDeadline) || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="最早实发时间">
              {{ formatInstant(detail.issueEarliestAt) || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="版本号">
              {{ detail.revisionNo ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="申请人">
              {{ detail.applicantName || detail.applicantId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatInstant(detail.createTime) || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="申领单 ID">
              {{ detail.id }}
            </el-descriptions-item>
            <el-descriptions-item label="共享单据 ID">
              {{ detail.bizDocumentId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item
              label="业务链 ID"
              :span="2"
            >
              {{ detail.businessChainId || '—' }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="detail.approvedAt"
              label="审核时间"
            >
              {{ formatInstant(detail.approvedAt) }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="detail.cancelledAt"
              label="取消时间"
            >
              {{ formatInstant(detail.cancelledAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="card claim_detail__section">
          <div class="claim_detail__section_head">
            <h4 class="claim_detail__section_title">
              当前版本物料
            </h4>
            <span class="t-hint">未领取合计：<ItemQuantityText :value="unclaimedTotal" /></span>
          </div>
          <el-table
            :data="detail.lines || []"
            class="table-scroll-x"
            row-key="id"
          >
            <el-table-column
              label="物料"
              min-width="180"
            >
              <template #default="{ row }">
                <div class="claim_detail__strong">
                  {{ row.materialName || row.materialId }}
                </div>
                <div class="t-hint">
                  {{ [row.specification, materialTypeText(row.materialType)].filter(Boolean).join(' · ') }}
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
              label="已实发"
              min-width="100"
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
              min-width="100"
              align="right"
            >
              <template #default="{ row }">
                <ItemQuantityText
                  :value="row.unclaimedQuantity"
                  :unit="row.baseUnit"
                  class="claim_detail__unclaimed"
                />
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="card claim_detail__section">
          <h4 class="claim_detail__section_title">
            计划分配
          </h4>
          <el-table
            :data="detail.allocations || []"
            class="table-scroll-x"
            empty-text="当前版本暂无计划分配"
            row-key="id"
          >
            <el-table-column
              label="物料"
              min-width="170"
            >
              <template #default="{ row }">
                {{ materialName(row.materialId) }}
              </template>
            </el-table-column>
            <el-table-column
              label="库位"
              min-width="170"
            >
              <template #default="{ row }">
                <div>{{ row.locationName || row.locationId || '—' }}</div>
                <div class="t-hint">
                  {{ row.locationCode || '' }}
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="计划数量"
              min-width="110"
              align="right"
            >
              <template #default="{ row }">
                <ItemQuantityText :value="row.plannedQuantity" />
              </template>
            </el-table-column>
            <el-table-column
              label="分配 ID"
              min-width="170"
            >
              <template #default="{ row }">
                <span class="t-hint">{{ row.id }}</span>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="card claim_detail__section">
          <h4 class="claim_detail__section_title">
            预占记录与策略
          </h4>
          <el-table
            :data="detail.reservations || []"
            class="table-scroll-x"
            empty-text="当前版本暂无预占记录"
            row-key="id"
          >
            <el-table-column
              label="物料 / 库位"
              min-width="180"
            >
              <template #default="{ row }">
                <div>{{ materialName(row.materialId) }}</div>
                <div class="t-hint">
                  {{ row.locationName || row.locationId || '—' }}
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="资产"
              min-width="120"
            >
              <template #default="{ row }">
                {{ row.assetCode || (row.fixedAssetId ? `资产 ${row.fixedAssetId}` : '—') }}
              </template>
            </el-table-column>
            <el-table-column
              label="预占策略"
              min-width="190"
            >
              <template #default="{ row }">
                <div>{{ reservationRuleLabel(row.reservationRule) }}</div>
                <div class="t-hint">
                  {{ formatRange(row.reservationStartAt, row.reservationEndAt) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="预占 / 消耗 / 释放 / 剩余"
              min-width="190"
              align="right"
            >
              <template #default="{ row }">
                <div class="claim_detail__quantities">
                  <ItemQuantityText :value="row.reservedQuantity" /> /
                  <ItemQuantityText :value="row.consumedQuantity" /> /
                  <ItemQuantityText :value="row.releasedQuantity" /> /
                  <ItemQuantityText :value="row.remainingQuantity" />
                </div>
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
          </el-table>
          <p class="t-hint claim_detail__notice">
            预占不等于实发保证，实发时仍会重新校验实际库存。
          </p>
        </section>

        <section class="card claim_detail__section">
          <div class="claim_detail__section_head">
            <div>
              <h4 class="claim_detail__section_title">
                领取方式
              </h4>
              <p class="t-hint">
                学生代领仅在授权有效期内可实发；首次实发后不可再推送、改派或撤回。
              </p>
            </div>
            <div
              v-if="detail.canDelegate"
              class="claim_detail__delegation_actions"
            >
              <el-button
                type="primary"
                plain
                @click="openDelegation"
              >
                {{ detail.pickupMode === 'ASSISTANT' ? '改派助理' : '推送代领' }}
              </el-button>
              <el-button
                v-if="detail.pickupMode === 'ASSISTANT'"
                type="danger"
                plain
                :loading="actionLoading === 'delegation'"
                @click="revokeDelegation"
              >
                撤回代领
              </el-button>
            </div>
          </div>
          <el-descriptions
            :column="2"
            border
          >
            <el-descriptions-item label="领取方式">
              {{ pickupModeLabel(detail.pickupMode) }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="detail.pickupUserName || detail.pickupUserId"
              label="实际领取人"
            >
              {{ detail.pickupUserName || detail.pickupUserId }}
            </el-descriptions-item>
            <template v-if="detail.pickupMode === 'ASSISTANT'">
              <el-descriptions-item label="代领学生">
                {{ detail.delegateStudentName || detail.delegateStudentId || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="学生账号">
                {{ detail.delegateStudentAccount || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="推送时间">
                {{ formatInstant(detail.delegatedAt) || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="授权状态">
                <span :class="detail.delegationValid ? 'claim_detail__valid' : 'claim_detail__invalid'">
                  {{ detail.delegationValid ? '授权有效' : '授权失效' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item
                label="授权 ID"
                :span="2"
              >
                {{ detail.delegateGrantId || '—' }}
              </el-descriptions-item>
            </template>
          </el-descriptions>
          <el-alert
            v-if="detail.pickupMode === 'ASSISTANT' && detail.delegationBlockedReason"
            :title="detail.delegationBlockedReason"
            type="warning"
            :closable="false"
            show-icon
            class="claim_detail__delegation_alert"
          />
        </section>

        <section class="card claim_detail__section">
          <h4 class="claim_detail__section_title">
            申领事件
          </h4>
          <el-timeline
            v-if="detail.events?.length"
            class="claim_detail__timeline"
          >
            <el-timeline-item
              v-for="event in detail.events"
              :key="event.id"
              :timestamp="formatInstant(event.occurredAt) || '—'"
              placement="top"
            >
              <div class="claim_detail__event_title">
                {{ eventTypeLabel(event.eventType) }}
              </div>
              <div class="t-hint">
                {{ event.actorName || event.actorId || '系统' }}
                <span v-if="event.fromStatus || event.toStatus">
                  ·
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
              </div>
              <div
                v-if="event.reason"
                class="claim_detail__event_reason"
              >
                {{ event.reason }}
              </div>
              <div class="t-hint">
                事件 ID：{{ event.id }}
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty
            v-else
            description="暂无申领事件"
            :image-size="60"
          />
        </section>

        <section class="card claim_detail__section">
          <div class="claim_detail__section_head">
            <div>
              <h4 class="claim_detail__section_title">
                三单业务链与库存单据
              </h4>
              <p class="t-hint">
                申领单、实发单、归还单通过业务链 ID 关联；以下展示后端返回的全部库存单据。
              </p>
            </div>
            <el-button
              link
              type="primary"
              :loading="documentsLoading"
              @click="loadDocuments"
            >
              刷新业务链
            </el-button>
          </div>
          <el-alert
            title="业务链 ID"
            type="info"
            :closable="false"
            class="claim_detail__chain_alert"
          >
            {{ documentChain?.businessChainId || detail.businessChainId || '—' }}
          </el-alert>
          <el-collapse
            v-if="documentChain?.documents?.length"
            class="claim_detail__documents"
          >
            <el-collapse-item
              v-for="stockDocument in documentChain.documents"
              :key="stockDocument.document.id"
              :name="String(stockDocument.document.id)"
            >
              <template #title>
                <div class="claim_detail__document_title">
                  <span>{{ documentTypeLabel(stockDocument.document.documentType) }}</span>
                  <span class="t-strong">{{ stockDocument.document.documentNo || stockDocument.document.id }}</span>
                  <span class="t-hint">序号 {{ stockDocument.document.documentSeq ?? '—' }}</span>
                </div>
              </template>
              <el-descriptions
                :column="2"
                border
                class="claim_detail__document_meta"
              >
                <el-descriptions-item label="单据 ID">
                  {{ stockDocument.document.id }}
                </el-descriptions-item>
                <el-descriptions-item label="共享单据 ID">
                  {{ stockDocument.document.bizDocumentId || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="来源单据 ID">
                  {{ stockDocument.document.sourceMaterialDocumentId || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="业务场景">
                  {{ stockDocument.document.businessScene || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="库位">
                  {{ stockDocument.document.locationName || stockDocument.document.locationId || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="实际领取人">
                  {{ stockDocument.document.pickupUserName || stockDocument.document.pickupUserId || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="过账时间">
                  {{ formatInstant(stockDocument.document.postedAt) || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="处理状态">
                  {{ stockDocument.document.processStatus || '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="归还状态">
                  {{ stockDocument.document.returnStatus || '—' }}
                </el-descriptions-item>
                <el-descriptions-item
                  v-if="stockDocument.document.overdueReason"
                  label="逾期原因"
                  :span="2"
                >
                  {{ stockDocument.document.overdueReason }}
                </el-descriptions-item>
              </el-descriptions>

              <h5 class="claim_detail__sub_title">
                单据行
              </h5>
              <el-table
                :data="stockDocument.lines || []"
                class="table-scroll-x"
                empty-text="暂无单据行"
                row-key="id"
              >
                <el-table-column
                  label="行 ID"
                  min-width="160"
                >
                  <template #default="{ row }">
                    <span class="t-hint">{{ row.id }}</span>
                  </template>
                </el-table-column>
                <el-table-column
                  label="物料"
                  min-width="160"
                >
                  <template #default="{ row }">
                    <div>{{ row.materialName || row.materialId }}</div>
                    <div class="t-hint">
                      {{ row.specification || '' }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column
                  label="数量"
                  min-width="100"
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
                  label="资产 / 位置"
                  min-width="150"
                >
                  <template #default="{ row }">
                    {{ row.assetCode || row.positionText || row.fixedAssetId || '—' }}
                  </template>
                </el-table-column>
              </el-table>

              <h5 class="claim_detail__sub_title">
                单据事件
              </h5>
              <el-table
                :data="stockDocument.events || []"
                class="table-scroll-x"
                empty-text="暂无单据事件"
                row-key="id"
              >
                <el-table-column
                  label="事件"
                  min-width="130"
                >
                  <template #default="{ row }">
                    {{ eventTypeLabel(row.eventType) }}
                  </template>
                </el-table-column>
                <el-table-column
                  label="状态变化"
                  min-width="220"
                >
                  <template #default="{ row }">
                    <ItemStatusPill
                      v-if="row.fromStatus"
                      :status="row.fromStatus"
                    /><span v-else>—</span>
                    →
                    <ItemStatusPill
                      v-if="row.toStatus"
                      :status="row.toStatus"
                    /><span v-else>—</span>
                  </template>
                </el-table-column>
                <el-table-column
                  label="操作人"
                  min-width="110"
                >
                  <template #default="{ row }">
                    {{ row.actorName || row.actorId || '系统' }}
                  </template>
                </el-table-column>
                <el-table-column
                  label="发生时间"
                  min-width="160"
                >
                  <template #default="{ row }">
                    {{ formatInstant(row.occurredAt) || '—' }}
                  </template>
                </el-table-column>
                <el-table-column
                  label="原因 / 事件 ID"
                  min-width="180"
                >
                  <template #default="{ row }">
                    <div>{{ row.reason || '—' }}</div>
                    <div class="t-hint">
                      {{ row.id }}
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
          <el-empty
            v-else-if="!documentsLoading"
            description="暂无实发或归还等库存单据"
            :image-size="60"
          />
        </section>
      </template>
      <el-empty
        v-else-if="!loading"
        description="申领详情不存在或无权查看"
        :image-size="80"
      />
    </div>

    <template
      v-if="detail"
      #footer
    >
      <div class="claim_detail__footer">
        <el-button @click="emit('close')">
          关闭
        </el-button>
        <div class="claim_detail__actions">
          <el-button
            v-if="detail.canRebook"
            @click="emit('rebook', detail)"
          >
            再次预约
          </el-button>
          <el-button
            v-if="detail.canModify"
            type="primary"
            plain
            @click="emit('edit', detail)"
          >
            修改申领
          </el-button>
          <el-button
            v-if="detail.canAbandon"
            type="warning"
            :loading="actionLoading === 'abandon'"
            @click="promptAction('abandon')"
          >
            放弃剩余
          </el-button>
          <el-button
            v-if="detail.canCancel"
            type="danger"
            :loading="actionLoading === 'cancel'"
            @click="promptAction('cancel')"
          >
            取消申领
          </el-button>
        </div>
      </div>
    </template>
  </el-drawer>

  <el-dialog
    v-model="delegationVisible"
    :title="detail?.pickupMode === 'ASSISTANT' ? '改派代领助理' : '推送学生代领'"
    width="min(480px, 94vw)"
    append-to-body
    destroy-on-close
  >
    <el-alert
      title="仅可选择当前教师名下处于有效授权期的学生；提交后由后端再次校验。"
      type="info"
      :closable="false"
      show-icon
      class="claim_detail__delegation_dialog_alert"
    />
    <el-form
      label-position="top"
      @submit.prevent
    >
      <el-form-item
        label="代领学生"
        required
      >
        <el-select
          v-model="delegationStudentId"
          v-loading="assistantsLoading"
          filterable
          placeholder="选择有效助理"
          class="claim_detail__delegation_select"
        >
          <el-option
            v-for="assistant in assistants"
            :key="assistant.id"
            :label="`${assistant.studentName || assistant.studentId}${assistant.studentAccount ? ` · ${assistant.studentAccount}` : ''}`"
            :value="String(assistant.studentId)"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="delegationVisible = false">
        取消
      </el-button>
      <el-button
        type="primary"
        :disabled="!delegationStudentId || assistantsLoading"
        :loading="delegationSubmitting"
        @click="submitDelegation"
      >
        确认{{ detail?.pickupMode === 'ASSISTANT' ? '改派' : '推送' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import {
  abandonClaim,
  cancelClaim,
  getClaimDetail,
  getClaimDocuments,
  updateClaimDelegation
} from '@/api/item/claim'
import { pageAssistants } from '@/api/item/assistant'
import { formatInstant, formatRange } from '@/utils/datetime'
import {
  documentTypeLabel,
  eventTypeLabel,
  pickupModeLabel,
  reservationRuleLabel
} from './claim.constants'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  claimId: { type: [String, Number], default: '' }
})
const emit = defineEmits(['close', 'edit', 'rebook', 'changed'])

const detail = ref(null)
const documentChain = ref(null)
const loading = ref(false)
const documentsLoading = ref(false)
const actionLoading = ref('')
const delegationVisible = ref(false)
const delegationStudentId = ref('')
const delegationSubmitting = ref(false)
const assistants = ref([])
const assistantsLoading = ref(false)
let loadSerial = 0

const narrowMql = window.matchMedia('(max-width: 767px)')
const drawerSize = ref(narrowMql.matches ? '100%' : '980px')
function onNarrowChange(event) {
  drawerSize.value = event.matches ? '100%' : '980px'
}
onMounted(() => narrowMql.addEventListener('change', onNarrowChange))
onBeforeUnmount(() => narrowMql.removeEventListener('change', onNarrowChange))

const unclaimedTotal = computed(() => (detail.value?.lines || [])
  .reduce((total, line) => total + Number(line.unclaimedQuantity || 0), 0))
const cancelReason = computed(() => {
  const events = detail.value?.events || []
  return [...events].reverse().find((event) => event.eventType === 'CANCEL')?.reason || ''
})
const closeReasonText = computed(() => {
  if (detail.value?.closeReason === 'ABANDONED') return `申请人放弃剩余${detail.value.abandonReason ? `：${detail.value.abandonReason}` : ''}`
  return detail.value?.closeReason || '—'
})

function materialTypeText(type) {
  return type === 'FIXED_ASSET' ? '固定资产' : type === 'CONSUMABLE' ? '耗材' : ''
}

function materialName(materialId) {
  return detail.value?.lines?.find((line) => String(line.materialId) === String(materialId))?.materialName
    || materialId || '—'
}

async function loadDocuments() {
  if (!props.claimId) return
  documentsLoading.value = true
  try {
    documentChain.value = await getClaimDocuments(String(props.claimId))
  } catch {
    documentChain.value = null
  } finally {
    documentsLoading.value = false
  }
}

async function loadDetail() {
  const serial = ++loadSerial
  detail.value = null
  documentChain.value = null
  if (!props.claimId) return
  loading.value = true
  try {
    const result = await getClaimDetail(String(props.claimId))
    if (serial !== loadSerial) return
    detail.value = result
    await loadDocuments()
  } catch {
    if (serial === loadSerial) detail.value = null
  } finally {
    if (serial === loadSerial) loading.value = false
  }
}

async function openDelegation() {
  delegationVisible.value = true
  delegationStudentId.value = detail.value?.delegateStudentId
    ? String(detail.value.delegateStudentId)
    : ''
  assistants.value = []
  assistantsLoading.value = true
  try {
    const result = await pageAssistants({ pageNum: 1, pageSize: 100, status: 'ACTIVE' })
    assistants.value = result?.records || []
    const stillValid = assistants.value.some((item) => String(item.studentId) === delegationStudentId.value)
    if (!stillValid) delegationStudentId.value = ''
  } catch {
    assistants.value = []
    delegationStudentId.value = ''
  } finally {
    assistantsLoading.value = false
  }
}

async function submitDelegation() {
  if (assistantsLoading.value) return
  if (!delegationStudentId.value) {
    ElMessage.warning('请选择代领学生')
    return
  }
  delegationSubmitting.value = true
  try {
    const wasDelegated = detail.value.pickupMode === 'ASSISTANT'
    detail.value = await updateClaimDelegation(detail.value.id, delegationStudentId.value)
    ElMessage.success(wasDelegated ? '代领助理已改派' : '已推送学生代领')
    delegationVisible.value = false
    emit('changed', detail.value)
  } finally {
    delegationSubmitting.value = false
  }
}

async function revokeDelegation() {
  try {
    await ElMessageBox.confirm(
      `确定撤回 ${detail.value.delegateStudentName || '该学生'} 的代领安排吗？撤回后将恢复为本人领取。`,
      '撤回代领',
      { type: 'warning', confirmButtonText: '确定撤回', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  actionLoading.value = 'delegation'
  try {
    detail.value = await updateClaimDelegation(detail.value.id, null)
    ElMessage.success('已撤回代领，恢复为本人领取')
    emit('changed', detail.value)
  } finally {
    actionLoading.value = ''
  }
}

async function promptAction(action) {
  const isCancel = action === 'cancel'
  let reason
  try {
    const result = await ElMessageBox.prompt(
      isCancel ? '取消后当前预占将释放，确定取消这张申领吗？' : '放弃后将由管理员关闭剩余未领取部分，确定继续吗？',
      isCancel ? '取消申领' : '放弃剩余',
      {
        inputPlaceholder: '原因（选填，最多 512 字）',
        inputPattern: /^[\s\S]{0,512}$/,
        inputErrorMessage: '原因不能超过 512 字',
        confirmButtonText: isCancel ? '确定取消' : '确定放弃',
        cancelButtonText: '再想想',
        type: 'warning'
      }
    )
    reason = result.value?.trim() || undefined
  } catch {
    return
  }
  actionLoading.value = action
  try {
    const request = isCancel ? cancelClaim : abandonClaim
    detail.value = await request(String(detail.value.id), reason)
    ElMessage.success(isCancel ? '申领已取消' : '已提交放弃剩余申请')
    await loadDocuments()
    emit('changed', detail.value)
  } finally {
    actionLoading.value = ''
  }
}

function beforeClose(done) {
  if (!actionLoading.value && !delegationSubmitting.value) done()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) loadDetail()
    else {
      loadSerial += 1
      detail.value = null
      documentChain.value = null
      delegationVisible.value = false
      delegationStudentId.value = ''
      assistants.value = []
    }
  }
)
</script>

<style scoped>
.claim_detail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

.claim_detail__header h3,
.claim_detail__header p {
  margin: 0;
}

.claim_detail__header h3 {
  margin-bottom: 5px;
  font-size: 18px;
}

.claim_detail {
  display: flex;
  min-height: 240px;
  flex-direction: column;
  gap: 14px;
}

.claim_detail__section {
  padding: 18px;
}

.claim_detail__section_title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.claim_detail__section_head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.claim_detail__section_head .claim_detail__section_title {
  margin-bottom: 4px;
}

.claim_detail__section_head p {
  margin: 0;
}

.claim_detail__reason,
.claim_detail__event_reason {
  margin-top: 5px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.claim_detail__strong {
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.claim_detail__unclaimed :deep(.item_quantity_text__value) {
  color: var(--brand);
  font-weight: var(--fw-semibold);
}

.claim_detail__quantities {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.claim_detail__notice {
  margin: 10px 0 0;
}

.claim_detail__delegation_actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 8px;
}

.claim_detail__delegation_actions .el-button + .el-button {
  margin-left: 0;
}

.claim_detail__valid {
  color: var(--ok);
}

.claim_detail__invalid {
  color: var(--danger);
}

.claim_detail__delegation_alert,
.claim_detail__delegation_dialog_alert {
  margin-top: 12px;
}

.claim_detail__delegation_dialog_alert {
  margin: 0 0 16px;
}

.claim_detail__delegation_select {
  width: 100%;
}

.claim_detail__timeline {
  padding-left: 4px;
}

.claim_detail__event_title {
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.claim_detail__chain_alert {
  margin-bottom: 12px;
}

.claim_detail__document_title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-right: 12px;
}

.claim_detail__document_meta {
  margin-bottom: 16px;
}

.claim_detail__sub_title {
  margin: 16px 0 9px;
  font-size: 13px;
  font-weight: var(--fw-semibold);
  color: var(--g2);
}

.claim_detail__footer,
.claim_detail__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.claim_detail__actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 767px) {
  .claim_detail__section {
    padding: 14px;
  }

  .claim_detail__footer {
    align-items: stretch;
    flex-direction: column-reverse;
  }

  .claim_detail__actions {
    justify-content: flex-start;
  }
}
</style>
