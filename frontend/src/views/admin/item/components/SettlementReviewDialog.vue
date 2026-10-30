<template>
  <el-dialog
    v-model="visible"
    title="核销单复核"
    width="min(960px, 95vw)"
    destroy-on-close
  >
    <div v-loading="loading">
      <template v-if="detail?.document">
        <el-alert
          title="复核人与经办人不得为同一用户，后端会再次校验；批准后才执行实际库存或资产状态变更。"
          type="info"
          :closable="false"
          show-icon
          class="settlement_review__alert"
        />
        <el-descriptions
          :column="3"
          border
          size="small"
        >
          <el-descriptions-item label="核销单">
            {{ detail.document.documentNo }}
          </el-descriptions-item>
          <el-descriptions-item label="类型">
            {{ documentTypeLabel(detail.document.documentType) }}
          </el-descriptions-item>
          <el-descriptions-item label="版本">
            {{ detail.document.version }}
          </el-descriptions-item>
          <el-descriptions-item label="经办人">
            {{ detail.document.operatorName || detail.document.operatorId }}
          </el-descriptions-item>
          <el-descriptions-item label="发生时间">
            {{ formatTime(detail.document.occurredAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="来源移动单ID">
            {{ detail.document.sourceMaterialDocumentId || '—' }}
          </el-descriptions-item>
          <el-descriptions-item
            label="业务链ID"
            :span="3"
          >
            {{ detail.document.businessChainId || '—' }}
          </el-descriptions-item>
          <el-descriptions-item
            label="经办备注"
            :span="3"
          >
            {{ detail.document.remark || '—' }}
          </el-descriptions-item>
        </el-descriptions>
        <h3 class="settlement_review__section">
          核销明细与经办原因
        </h3>
        <el-table
          :data="detail.settlements || []"
          class="table-scroll-x"
          size="small"
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
            width="110"
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
            label="目标资产状态"
            width="130"
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
            label="经办原因"
            min-width="230"
            show-overflow-tooltip
          />
        </el-table>
        <el-form
          label-position="top"
          class="settlement_review__form"
        >
          <el-form-item
            label="复核结论"
            required
          >
            <el-radio-group v-model="approve">
              <el-radio-button :value="true">
                批准
              </el-radio-button>
              <el-radio-button :value="false">
                驳回
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="复核意见">
            <el-input
              v-model="reason"
              type="textarea"
              maxlength="512"
              show-word-limit
              placeholder="记录复核意见（可选）"
            />
          </el-form-item>
        </el-form>
      </template>
      <el-empty
        v-else-if="!loading"
        description="未获取到核销单"
      />
    </div>
    <template #footer>
      <el-button @click="visible = false">
        取消
      </el-button>
      <el-button
        type="primary"
        :disabled="detail?.document?.reviewStatus !== 'PENDING_REVIEW'"
        :loading="submitting"
        @click="submit"
      >
        提交复核
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import { getDocument, reviewSettlement } from '@/api/item/workflow'
import { formatInstant } from '@/utils/datetime'
import { documentTypeLabel, settlementTypeLabel } from '@/views/item/components/claim.constants'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  documentId: { type: [String, Number], default: '' }
})
const emit = defineEmits(['update:modelValue', 'success'])
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const detail = ref(null)
const approve = ref(true)
const reason = ref('')
const loading = ref(false)
const submitting = ref(false)
let requestSerial = 0
const formatTime = (value) => formatInstant(value) || '—'

watch(visible, (value) => {
  if (value) loadDetail()
  else {
    requestSerial += 1
    detail.value = null
    approve.value = true
    reason.value = ''
  }
})

async function loadDetail() {
  if (!props.documentId) return
  const serial = ++requestSerial
  loading.value = true
  try {
    const result = await getDocument(props.documentId)
    if (serial === requestSerial) detail.value = result
  } finally {
    if (serial === requestSerial) loading.value = false
  }
}

async function submit() {
  submitting.value = true
  try {
    const result = await reviewSettlement(props.documentId, {
      approve: approve.value,
      version: detail.value.document.version,
      reason: reason.value.trim() || undefined
    })
    ElMessage.success(approve.value ? '复核通过，库存处理已过账' : '核销单已驳回')
    visible.value = false
    emit('success', result)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.settlement_review__alert { margin-bottom: 14px; }
.settlement_review__section { margin: 18px 0 10px; font-size: 15px; }
.settlement_review__form { margin-top: 18px; }
</style>
