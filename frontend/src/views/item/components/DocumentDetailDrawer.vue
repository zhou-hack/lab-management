<template>
  <el-drawer
    :model-value="visible"
    title="单据详情"
    size="68%"
    @update:model-value="emitVisible"
  >
    <div v-loading="loading">
      <el-descriptions
        v-if="detail"
        :column="3"
        border
      >
        <el-descriptions-item label="单据 ID">
          {{ detail.id }}
        </el-descriptions-item>
        <el-descriptions-item label="类型">
          <StatusTag
            :value="detail.movementType"
            :map="movementTypeMap"
          />
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag
            :value="detail.status"
            :map="documentStatusMap"
          />
        </el-descriptions-item>
        <el-descriptions-item label="供应商">
          {{ detail.vendorName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="业务时间">
          {{ fmtShanghai(detail.occurredAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="过账时间">
          {{ fmtShanghai(detail.postedAt) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="经办人">
          {{ displayName(detail.operatorName, detail.operatorId) }}
        </el-descriptions-item>
        <el-descriptions-item label="金额合计">
          {{ formatQty(detail.totalAmount) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item
          label="事由"
          :span="2"
        >
          {{ detail.reason || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table
        v-if="detail"
        :data="detail.lines"
        border
        class="mt8"
      >
        <el-table-column
          label="行 ID（可复制）"
          width="200"
        >
          <template #default="{ row }">
            <span class="small">{{ row.id }}</span>
            <el-button
              link
              type="primary"
              class="ml4"
              @click="copyText(row.id)"
            >
              复制
            </el-button>
          </template>
        </el-table-column>
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
          prop="fromLocationName"
          label="出库位"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="toLocationName"
          label="入库位"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          label="数量"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.quantity) }}
          </template>
        </el-table-column>
        <el-table-column
          label="单价快照"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.unitPriceSnapshot) }}
          </template>
        </el-table-column>
        <el-table-column
          label="金额快照"
          width="110"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.amountSnapshot) }}
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getDocument } from '@/api/item'
import { documentStatusMap, movementTypeMap } from '@/utils/statusMap'
import { copyText, fmtShanghai, formatQty } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'

/**
 * 单据详情抽屉：打开时按 documentId 拉取详情
 */
const props = defineProps({
  visible: { type: Boolean, default: false },
  documentId: { type: String, default: '' }
})

const emit = defineEmits(['update:visible'])

const loading = ref(false)
const detail = ref(null)

function emitVisible(value) {
  emit('update:visible', value)
}

async function load() {
  if (!props.documentId) return
  loading.value = true
  detail.value = null
  try {
    detail.value = await getDocument(props.documentId)
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (open) => {
    if (open) load()
  }
)

/** 姓名/ID 均缺失（经办人未登记）时显示占位 */
function displayName(name, id) {
  if (name) return name
  return id || '-'
}
</script>
