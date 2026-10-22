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
        <div
          v-if="typeHint"
          class="type-hint"
        >
          {{ typeHint }}
        </div>
      </el-form-item>
      <el-form-item
        v-if="form.movementType === 'REVERSAL'"
        label="冲销原单 ID"
        required
      >
        <el-input
          v-model="form.reversalOfId"
          class="w240"
          maxlength="19"
          placeholder="从单据列表「冲销」按钮带入，或粘贴已过账单据 ID"
        />
        <div class="type-hint">
          仅期初 / 采购 / 领用 / 报废的已过账单据可冲销；明细行由原单自动复制，产生相反流水。
        </div>
      </el-form-item>
      <el-form-item
        v-if="form.movementType === 'PURCHASE_IN'"
        label="供应商"
      >
        <el-input
          v-model="form.vendorName"
          class="w240"
          maxlength="128"
          placeholder="选填，如国药集团试剂有限公司"
        />
      </el-form-item>
      <el-form-item
        label="事由"
        :required="reasonRequired"
      >
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="2"
          maxlength="2000"
          show-word-limit
          :placeholder="reasonPlaceholder"
        />
      </el-form-item>
    </el-form>

    <template v-if="form.movementType && form.movementType !== 'REVERSAL'">
      <el-table
        :data="form.lines"
        :row-class-name="rowClassName"
        border
      >
        <el-table-column
          type="index"
          label="#"
          width="48"
          align="center"
        />
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
              :placeholder="cols.toOptional ? '不填默认回原出库位' : '选择入库位'"
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
              maxlength="19"
              placeholder="从领用出库单详情复制行 ID"
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
              :precision="4"
              controls-position="right"
              placeholder="登记单价，4 位小数"
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
              controls-position="right"
              placeholder="必填，最多 3 位小数"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="cols.price"
          label="金额"
          width="120"
          align="right"
        >
          <template #default="{ row }">
            {{ lineAmount(row) }}
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
              :disabled="form.lines.length <= 1"
              @click="removeLine($index)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button
        class="add-line-btn"
        plain
        @click="addLine"
      >
        + 添加一行
      </el-button>
      <div
        v-if="cols.price && hasAmount"
        class="total-bar"
      >
        合计金额：<b>{{ totalAmount }}</b> 元（按 数量 × 单价 计算，两位小数）
      </div>
    </template>

    <div class="footer-tip">
      业务发生时间默认取提交时刻；草稿需经「审批 → 过账」后才更新库存余额与台账。
    </div>

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
 * 通过 presetType/presetId 支持从列表「冲销」按钮预置类型与原单 ID；
 * 校验定位到具体行（提示行号 + 错误行标红），入库类型实时联动金额合计
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

/** 类型流向说明：帮助理解各类单据的必填要点与过账影响 */
const TYPE_HINTS = {
  OPENING: '初始化库存余额入库；需填写入库位与登记单价（4 位小数），过账后进入移动加权平均计价。',
  PURCHASE_IN: '采购到货入库；需填写入库位与采购单价，供应商选填（≤128 字符）。',
  ISSUE: '领用出库；需填写出库位，出库成本由过账时账面均价自动快照，无需填写单价。',
  TRANSFER: '库间调拨；每行需填写调出与调入库位（不能相同），成本按调出库位均价结转，过账产生一出一入两条流水。',
  SCRAP: '报损报废出库；需填写出库位与报废事由（必填），过账按账面均价计损失。',
  RETURN_IN: '领用归还入库；每行绑定原领用出库行 ID，入库位不填默认回原出库位，过账后生成归还结算。',
  REVERSAL: '整单冲销已过账单据；明细行由原单自动复制生成相反流水，原单保留可追溯。'
}

const submitting = ref(false)
const form = reactive({
  movementType: null,
  reversalOfId: '',
  vendorName: '',
  reason: '',
  lines: []
})

/** 校验未通过时标红的行下标集合（下次提交时重新计算） */
const errorRows = ref(new Set())

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

const typeHint = computed(() => TYPE_HINTS[form.movementType] || '')

/** SCRAP / REVERSAL 事由后端必填（@NotBlank），表单项同步显示红星 */
const reasonRequired = computed(() => ['SCRAP', 'REVERSAL'].includes(form.movementType))

const reasonPlaceholder = computed(() => {
  if (form.movementType === 'SCRAP') return '必填：报废事由，如试剂过期失效'
  if (form.movementType === 'REVERSAL') return '必填：冲销原因，如入库登记数量录错'
  if (form.movementType === 'RETURN_IN') return '选填：如课后归还剩余试剂（数量差异建议说明原因）'
  return '选填'
})

/** 入库行金额联动：数量 × 单价（两位小数），未填全显示占位 */
function lineAmount(line) {
  if (line.quantity == null || line.unitPrice == null) return '—'
  return (line.quantity * line.unitPrice).toFixed(2)
}

/** 至少一行填全 数量+单价 才显示合计（全免填时 0.00 无意义） */
const hasAmount = computed(() =>
  form.lines.some((l) => l.quantity != null && l.unitPrice != null)
)

const totalAmount = computed(() =>
  form.lines
    .filter((l) => l.quantity != null && l.unitPrice != null)
    .reduce((sum, l) => sum + l.quantity * l.unitPrice, 0)
    .toFixed(2)
)

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
    errorRows.value = new Set()
  }
)

function onTypeChange() {
  form.lines = [emptyLine()]
  form.reversalOfId = ''
  errorRows.value = new Set()
}

function addLine() {
  form.lines.push(emptyLine())
}

function removeLine(index) {
  form.lines.splice(index, 1)
}

function rowClassName({ rowIndex }) {
  return errorRows.value.has(rowIndex) ? 'error-row' : ''
}

/** 校验失败：标红对应行并提示行号，聚焦定位 */
function failLine(index, message) {
  errorRows.value = new Set([index])
  ElMessage.warning(`第 ${index + 1} 行：${message}`)
  return false
}

/** 小数位数（el-input-number 去掉固定精度后由后端 @Digits 兜底，前端先校验给出明确提示） */
function decimalPlaces(value) {
  const text = String(value)
  const dot = text.indexOf('.')
  return dot === -1 ? 0 : text.length - dot - 1
}

function validateLines() {
  errorRows.value = new Set()
  if (form.movementType === 'REVERSAL') {
    const reversalOfId = form.reversalOfId.trim()
    if (!reversalOfId) {
      ElMessage.warning('请填写冲销原单 ID')
      return false
    }
    if (!/^\d{1,19}$/.test(reversalOfId)) {
      ElMessage.warning('冲销原单 ID 格式无效，应为纯数字')
      return false
    }
    if (!form.reason.trim()) {
      ElMessage.warning('冲销原因必填')
      return false
    }
    return true
  }
  if (!form.lines.length) {
    ElMessage.warning('请至少添加一行明细')
    return false
  }
  for (let i = 0; i < form.lines.length; i++) {
    const line = form.lines[i]
    if (cols.value.sku && !line.skuId) {
      return failLine(i, '物料未选择')
    }
    if (cols.value.from && !line.fromLocationId) {
      return failLine(i, '出库位未选择')
    }
    if (cols.value.to && !line.toLocationId) {
      return failLine(i, '入库位未选择')
    }
    const issueLineId = (line.originalIssueLineId || '').trim()
    if (cols.value.issueLine && !issueLineId) {
      return failLine(i, '原出库行 ID 未填写')
    }
    if (cols.value.issueLine && issueLineId && !/^\d{1,19}$/.test(issueLineId)) {
      return failLine(i, '原出库行 ID 格式无效，应为纯数字')
    }
    if (line.quantity === null || line.quantity === undefined || line.quantity <= 0) {
      return failLine(i, '数量必须大于 0')
    }
    if (decimalPlaces(line.quantity) > 3) {
      return failLine(i, '数量小数位超过 3 位（物料精度上限）')
    }
    if (cols.value.price && (line.unitPrice === null || line.unitPrice === undefined)) {
      return failLine(i, '入库行需填写单价')
    }
  }
  if (reasonRequired.value && !form.reason.trim()) {
    ElMessage.warning(form.movementType === 'SCRAP' ? '报废事由必填' : '冲销原因必填')
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
        vendorName: form.vendorName.trim() || undefined,
        reason: form.reason.trim() || undefined,
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
        reason: form.reason.trim() || undefined,
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
        reason: form.reason.trim(),
        lines: form.lines.map((l) => ({
          skuId: l.skuId,
          fromLocationId: l.fromLocationId,
          quantity: l.quantity
        }))
      })
    } else if (type === 'RETURN_IN') {
      id = await createReturnIn({
        occurredAt,
        reason: form.reason.trim() || undefined,
        lines: form.lines.map((l) => ({
          originalIssueLineId: (l.originalIssueLineId || '').trim(),
          toLocationId: l.toLocationId || undefined,
          quantity: l.quantity
        }))
      })
    } else if (type === 'REVERSAL') {
      id = await createReversal({
        reversalOfId: form.reversalOfId.trim(),
        reason: form.reason.trim()
      })
    }
    ElMessage.success(`登记成功，草稿单据 ID：${id}；审批通过后过账生效`)
    emitVisible(false)
    emit('created')
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.type-hint {
  width: 100%;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
  margin-top: 2px;
}

.add-line-btn {
  width: 100%;
  margin-top: 8px;
  margin-left: 0;
  border-style: dashed;
}

.total-bar {
  margin-top: 8px;
  text-align: right;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.footer-tip {
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

/* 校验未通过行标红（浅红背景，下次提交重新计算） */
:deep(.el-table .error-row) {
  --el-table-tr-bg-color: var(--el-color-danger-light-9);
}
</style>
