<template>
  <div class="inbound_panel">
    <div class="card inbound_panel__card">
      <div class="inbound_panel__toolbar">
        <el-alert
          class="inbound_panel__notice"
          type="info"
          :closable="false"
          title="下方手工表单仅用于数量跟踪耗材；物料清单导入支持耗材数量入库与固定资产逐台建档。"
        />
        <el-button
          type="primary"
          plain
          @click="importVisible = true"
        >
          导入物料清单
        </el-button>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        label-position="top"
        @submit.prevent
      >
        <div class="inbound_panel__meta">
          <el-form-item
            label="入库类型"
            prop="documentType"
            class="inbound_panel__type"
          >
            <el-radio-group
              v-model="form.documentType"
              @change="onDocumentTypeChange"
            >
              <el-radio-button value="OPENING">
                期初入库
              </el-radio-button>
              <el-radio-button value="REPLENISHMENT">
                补货入库
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>

        <div
          v-for="(line, index) in form.lines"
          :key="line._key"
          class="inbound_panel__line"
        >
          <div class="inbound_panel__line_head">
            <span>入库行 {{ index + 1 }}</span>
            <el-button
              link
              type="danger"
              :disabled="form.lines.length === 1"
              @click="removeLine(index)"
            >
              删除
            </el-button>
          </div>
          <div class="inbound_panel__line_grid">
            <el-form-item
              label="耗材物料"
              :prop="`lines.${index}.materialId`"
              :rules="lineRules.materialId"
            >
              <item-material-select
                v-model="line.materialId"
                material-type="CONSUMABLE"
                :clearable="false"
                placeholder="搜索并选择耗材"
              />
            </el-form-item>
            <el-form-item
              label="入库库位"
              :prop="`lines.${index}.locationId`"
              :rules="lineRules.locationId"
            >
              <item-location-select
                v-model="line.locationId"
                :clearable="false"
                placeholder="选择库位"
              />
            </el-form-item>
            <el-form-item
              label="库内位置"
              :prop="`lines.${index}.positionText`"
              :rules="lineRules.positionText"
            >
              <el-input
                v-model="line.positionText"
                maxlength="256"
                placeholder="选填，如 A 区 3 号柜"
              />
            </el-form-item>
            <el-form-item
              label="入库数量"
              :prop="`lines.${index}.quantity`"
              :rules="lineRules.quantity"
            >
              <el-input-number
                v-model="line.quantity"
                :min="1"
                :precision="0"
                :step="1"
                controls-position="right"
                class="inbound_panel__quantity"
              />
            </el-form-item>
          </div>
        </div>

        <el-button
          class="inbound_panel__add"
          plain
          @click="addLine"
        >
          添加入库行
        </el-button>
        <el-form-item
          label="备注"
          prop="remark"
          class="inbound_panel__remark"
        >
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
        <div class="inbound_panel__actions">
          <el-button @click="resetForm">
            清空重填
          </el-button>
          <el-button
            type="primary"
            :loading="submitting"
            @click="submit"
          >
            提交{{ form.documentType === 'OPENING' ? '期初' : '补货' }}入库
          </el-button>
        </div>
      </el-form>
    </div>

    <div
      v-if="createdDetail?.document"
      class="card inbound_panel__result"
    >
      <el-alert
        type="success"
        :closable="false"
        title="入库成功，单据已生成"
      />
      <el-descriptions
        :column="3"
        border
        class="inbound_panel__result_meta"
      >
        <el-descriptions-item label="单据编号">
          {{ createdDetail.document.documentNo }}
        </el-descriptions-item>
        <el-descriptions-item label="业务类型">
          {{ sceneLabel(createdDetail.document.businessScene) }}
        </el-descriptions-item>
        <el-descriptions-item label="过账状态">
          <item-status-pill :status="createdDetail.document.processStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="操作人">
          {{ createdDetail.document.operatorName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="发生时间">
          {{ formatInstant(createdDetail.document.occurredAt, { withSeconds: true }) || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注">
          {{ createdDetail.document.remark || '—' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table
        :data="createdDetail.lines || []"
        row-key="id"
      >
        <el-table-column
          prop="materialName"
          label="耗材"
          min-width="150"
        />
        <el-table-column
          label="规格型号"
          min-width="130"
        >
          <template #default="{ row }">
            {{ row.specification || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="库内位置"
          min-width="130"
        >
          <template #default="{ row }">
            {{ row.positionText || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="入库数量"
          min-width="120"
          align="right"
        >
          <template #default="{ row }">
            <item-quantity-text
              :value="row.quantity"
              :unit="row.baseUnit"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <asset-import-dialog
      v-model="importVisible"
      @success="onImportSuccess"
    />
  </div>
</template>

<script setup>
import { nextTick, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemMaterialSelect from '@/components/item/ItemMaterialSelect.vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import AssetImportDialog from '@/components/item/AssetImportDialog.vue'
import { createOpening, createReplenishment } from '@/api/item/stock'
import { formatInstant } from '@/utils/datetime'

const formRef = ref()
const submitting = ref(false)
const importVisible = ref(false)
const createdDetail = ref(null)
let lineSequence = 0

function createIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() || `inbound-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function createLine() {
  lineSequence += 1
  return {
    _key: `line-${lineSequence}`,
    materialId: '',
    locationId: '',
    positionText: '',
    quantity: 1
  }
}

const form = reactive({
  documentType: 'OPENING',
  idempotencyKey: createIdempotencyKey(),
  remark: '',
  lines: [createLine()]
})

const lineRules = {
  materialId: [{ required: true, message: '请选择耗材物料', trigger: 'change' }],
  locationId: [{ required: true, message: '请选择入库库位', trigger: 'change' }],
  positionText: [{ max: 256, message: '库内位置不能超过256个字符', trigger: 'blur' }],
  quantity: [
    { required: true, message: '请输入入库数量', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
          callback(new Error('入库数量必须大于0'))
        } else if (!Number.isInteger(value)) {
          callback(new Error('入库数量必须为整数'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

function addLine() {
  form.lines.push(createLine())
}

function removeLine(index) {
  if (form.lines.length > 1) form.lines.splice(index, 1)
}

function onDocumentTypeChange() {
  form.idempotencyKey = createIdempotencyKey()
  createdDetail.value = null
}

function resetForm() {
  form.documentType = 'OPENING'
  form.idempotencyKey = createIdempotencyKey()
  form.remark = ''
  form.lines = [createLine()]
  createdDetail.value = null
  nextTick(() => formRef.value?.clearValidate())
}

function sceneLabel(scene) {
  return { OPENING: '期初入库', REPLENISHMENT: '补货入库' }[scene] || scene || '—'
}

function onImportSuccess(result) {
  ElMessage.success(`已新增 ${result.importedAssetCount} 台固定资产，耗材入库数量 ${result.importedConsumableQuantity}`)
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (form.lines.some((line) => !Number.isInteger(Number(line.quantity)))) {
    ElMessage.warning('入库数量必须为整数')
    return
  }
  submitting.value = true
  try {
    const payload = {
      lines: form.lines.map((line) => ({
        materialId: line.materialId,
        locationId: line.locationId,
        positionText: line.positionText.trim() || null,
        quantity: line.quantity
      })),
      idempotencyKey: form.idempotencyKey.trim() || null,
      remark: form.remark.trim() || null
    }
    createdDetail.value = form.documentType === 'OPENING'
      ? await createOpening(payload)
      : await createReplenishment(payload)
    ElMessage.success('耗材入库成功')
    form.idempotencyKey = createIdempotencyKey()
    form.remark = ''
    form.lines = [createLine()]
    nextTick(() => formRef.value?.clearValidate())
  } catch {
    // 请求层已展示具体错误，表单与幂等键保持不变供重试
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.inbound_panel__card,
.inbound_panel__result {
  padding: 18px;
}

.inbound_panel__toolbar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.inbound_panel__toolbar .inbound_panel__notice {
  flex: 1;
}

.inbound_panel__toolbar > .el-button {
  flex-shrink: 0;
}

.inbound_panel__notice {
  margin-bottom: 18px;
}

.inbound_panel__meta {
  display: grid;
  grid-template-columns: minmax(280px, .8fr) minmax(320px, 1.2fr);
  gap: 16px;
}

.inbound_panel__line {
  padding: 14px 16px 2px;
  margin-bottom: 12px;
  background: var(--surface-sunken);
  border: .5px solid var(--hair);
  border-radius: var(--r-md);
}

.inbound_panel__line_head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: var(--fw-medium);
  color: var(--g2);
}

.inbound_panel__line_grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 0 16px;
}

.inbound_panel__quantity {
  width: 100%;
}

.inbound_panel__add {
  margin-bottom: 18px;
}

.inbound_panel__remark {
  margin-top: 4px;
}

.inbound_panel__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.inbound_panel__result {
  margin-top: 14px;
}

.inbound_panel__result_meta {
  margin: 16px 0;
}

@media (max-width: 768px) {
  .inbound_panel__card,
  .inbound_panel__result {
    padding: 14px;
  }

  .inbound_panel__meta,
  .inbound_panel__line_grid {
    grid-template-columns: 1fr;
  }
}
</style>
