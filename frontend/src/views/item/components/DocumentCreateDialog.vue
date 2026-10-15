<template>
  <el-dialog
    :model-value="visible"
    title="新建单据（登记为草稿）"
    width="1000px"
    @update:model-value="emitVisible"
  >
    <el-form label-width="110px">
      <el-form-item
        label="单据类型"
        required
      >
        <el-select
          v-model="form.movementType"
          class="w240"
          @change="onTypeChange"
        >
          <el-option
            v-for="t in CREATE_TYPES"
            :key="t.value"
            :label="t.label"
            :value="t.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="form.movementType === 'REVERSAL'"
        label="冲销原单 ID"
        required
      >
        <el-input
          v-model="form.reversalOfId"
          class="w240"
          placeholder="已过账的期初/采购/领用/报废单 ID"
        />
      </el-form-item>
      <el-form-item
        v-if="form.movementType === 'PURCHASE_IN'"
        label="供应商"
      >
        <el-input
          v-model="form.vendorName"
          class="w240"
          placeholder="选填"
        />
      </el-form-item>
      <el-form-item label="事由">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="2"
          :placeholder="form.movementType === 'SCRAP' ? '报废事由必填' : '选填'"
        />
      </el-form-item>
    </el-form>

    <template v-if="form.movementType && form.movementType !== 'REVERSAL'">
      <el-table
        :data="form.lines"
        border
      >
        <el-table-column
          v-if="cols.sku"
          label="物料"
          min-width="230"
        >
          <template #default="{ row }">
            <SkuSelect
              v-model="row.skuId"
              placeholder="搜索物料"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="cols.from"
          label="出库位"
          min-width="190"
        >
          <template #default="{ row }">
            <LocationSelect
              v-model="row.fromLocationId"
              placeholder="选择出库位"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="cols.to || cols.toOptional"
          label="入库位"
          min-width="190"
        >
          <template #default="{ row }">
            <LocationSelect
              v-model="row.toLocationId"
              :placeholder="cols.toOptional ? '默认回原出库位' : '选择入库位'"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="cols.price"
          label="单价"
          width="150"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.unitPrice"
              :min="0"
              :step="0.1"
              :precision="4"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="cols.issueLine"
          label="原出库行 ID"
          min-width="200"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.originalIssueLineId"
              placeholder="从领用出库单详情复制行 ID"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="数量"
          width="150"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="0"
              :step="1"
              :precision="3"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="80"
        >
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              @click="removeLine($index)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button
        class="mt8"
        @click="addLine"
      >
        + 添加行
      </el-button>
    </template>

    <template #footer>
      <el-button @click="emitVisible(false)">
        取消
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="submitCreate"
      >
        提交（登记草稿）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createDocument,
  createReturnIn,
  createReversal,
  createScrap,
  createTransfer
} from '@/api/item'
import { nowUtcIso } from '@/utils/format'
import SkuSelect from '@/components/SkuSelect.vue'
import LocationSelect from '@/components/LocationSelect.vue'

/**
 * 新建库存单据弹窗（7 类单据统一登记为草稿）
 * 通过 presetType/presetId 支持从列表「冲销」按钮预置类型与原单 ID
 */
const props = defineProps({
  visible: { type: Boolean, default: false },
  presetType: { type: String, default: '' },
  presetId: { type: String, default: '' }
})

const emit = defineEmits(['update:visible', 'created'])

const CREATE_TYPES = [
  { value: 'OPENING', label: '期初入库 OPENING' },
  { value: 'PURCHASE_IN', label: '采购入库 PURCHASE_IN' },
  { value: 'ISSUE', label: '领用出库 ISSUE' },
  { value: 'TRANSFER', label: '调拨 TRANSFER' },
  { value: 'SCRAP', label: '报损报废 SCRAP' },
  { value: 'RETURN_IN', label: '归还入库 RETURN_IN' },
  { value: 'REVERSAL', label: '冲销 REVERSAL' }
]

const submitting = ref(false)
const form = reactive({
  movementType: null,
  reversalOfId: '',
  vendorName: '',
  reason: '',
  lines: []
})

/** 类型 → 明细行条件列联动 */
const cols = computed(() => {
  const t = form.movementType
  return {
    sku: ['OPENING', 'PURCHASE_IN', 'ISSUE', 'TRANSFER', 'SCRAP'].includes(t),
    from: ['ISSUE', 'SCRAP', 'TRANSFER'].includes(t),
    to: ['OPENING', 'PURCHASE_IN', 'TRANSFER'].includes(t),
    toOptional: t === 'RETURN_IN',
    price: ['OPENING', 'PURCHASE_IN'].includes(t),
    issueLine: t === 'RETURN_IN'
  }
})

function emitVisible(value) {
  emit('update:visible', value)
}

function emptyLine() {
  return { skuId: null, fromLocationId: null, toLocationId: null, quantity: null, unitPrice: null, originalIssueLineId: '' }
}

/** 打开时按预置值初始化（裸 @click 传入的 MouseEvent 已被父级防御为字符串） */
watch(
  () => props.visible,
  (open) => {
    if (!open) return
    form.movementType = props.presetType || null
    form.reversalOfId = props.presetId || ''
    form.vendorName = ''
    form.reason = ''
    form.lines = [emptyLine()]
  }
)

function onTypeChange() {
  form.lines = [emptyLine()]
  form.reversalOfId = ''
}

function addLine() {
  form.lines.push(emptyLine())
}

function removeLine(index) {
  form.lines.splice(index, 1)
}

function validateLines() {
  if (form.movementType === 'REVERSAL') {
    if (!form.reversalOfId) {
      ElMessage.warning('请填写冲销原单 ID')
      return false
    }
    return true
  }
  if (!form.lines.length) {
    ElMessage.warning('请至少添加一行明细')
    return false
  }
  for (const line of form.lines) {
    if (cols.value.sku && !line.skuId) {
      ElMessage.warning('存在未选择物料的明细行')
      return false
    }
    if (cols.value.from && !line.fromLocationId) {
      ElMessage.warning('存在未选择出库位的明细行')
      return false
    }
    if (cols.value.to && !line.toLocationId) {
      ElMessage.warning('存在未选择入库位的明细行')
      return false
    }
    if (cols.value.issueLine && !line.originalIssueLineId) {
      ElMessage.warning('存在未填写原出库行 ID 的明细行')
      return false
    }
    if (line.quantity === null || line.quantity === undefined || line.quantity <= 0) {
      ElMessage.warning('明细行数量必须大于 0')
      return false
    }
    if (cols.value.price && (line.unitPrice === null || line.unitPrice === undefined)) {
      ElMessage.warning('入库行需填写单价')
      return false
    }
  }
  if (form.movementType === 'SCRAP' && !form.reason) {
    ElMessage.warning('报废单事由必填')
    return false
  }
  return true
}

async function submitCreate() {
  if (!form.movementType) {
    ElMessage.warning('请选择单据类型')
    return
  }
  if (!validateLines()) return
  submitting.value = true
  try {
    const occurredAt = nowUtcIso()
    const type = form.movementType
    let id
    if (type === 'OPENING' || type === 'PURCHASE_IN' || type === 'ISSUE') {
      id = await createDocument({
        movementType: type,
        occurredAt,
        vendorName: form.vendorName || undefined,
        reason: form.reason || undefined,
        lines: form.lines.map((l) => ({
          skuId: l.skuId,
          toLocationId: type === 'ISSUE' ? undefined : l.toLocationId,
          fromLocationId: type === 'ISSUE' ? l.fromLocationId : undefined,
          quantity: l.quantity,
          unitPrice: type === 'ISSUE' ? undefined : l.unitPrice
        }))
      })
    } else if (type === 'TRANSFER') {
      id = await createTransfer({
        occurredAt,
        reason: form.reason || undefined,
        lines: form.lines.map((l) => ({
          skuId: l.skuId,
          fromLocationId: l.fromLocationId,
          toLocationId: l.toLocationId,
          quantity: l.quantity
        }))
      })
    } else if (type === 'SCRAP') {
      id = await createScrap({
        occurredAt,
        reason: form.reason,
        lines: form.lines.map((l) => ({
          skuId: l.skuId,
          fromLocationId: l.fromLocationId,
          quantity: l.quantity
        }))
      })
    } else if (type === 'RETURN_IN') {
      id = await createReturnIn({
        occurredAt,
        reason: form.reason || undefined,
        lines: form.lines.map((l) => ({
          originalIssueLineId: l.originalIssueLineId,
          toLocationId: l.toLocationId || undefined,
          quantity: l.quantity
        }))
      })
    } else if (type === 'REVERSAL') {
      id = await createReversal({
        reversalOfId: form.reversalOfId,
        reason: form.reason || undefined
      })
    }
    ElMessage.success(`登记成功，草稿单据 ID：${id}`)
    emitVisible(false)
    emit('created')
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}
</script>
