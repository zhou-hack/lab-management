<template>
  <div class="transfer_panel">
    <div class="card transfer_panel__filters">
      <el-form
        :inline="true"
        @submit.prevent
      >
        <el-form-item label="状态">
          <el-select
            v-model="query.transferStatus"
            clearable
            placeholder="全部状态"
            class="transfer_panel__status"
          >
            <el-option
              label="待接收"
              value="PENDING_RECEIPT"
            />
            <el-option
              label="部分接收"
              value="PARTIALLY_RECEIVED"
            />
            <el-option
              label="待退回"
              value="PENDING_RETURN"
            />
            <el-option
              label="已关闭"
              value="CLOSED"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="源库位">
          <ItemLocationSelect
            v-model="query.fromLocationId"
            class="transfer_panel__location"
          />
        </el-form-item>
        <el-form-item label="目标库位">
          <ItemLocationSelect
            v-model="query.toLocationId"
            class="transfer_panel__location"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            @click="search"
          >
            查询
          </el-button>
          <el-button @click="reset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card">
      <div class="transfer_panel__head">
        <div>
          <h2 class="t-title">
            我相关的库位移动
          </h2>
          <p class="t-sub">
            源/目标库位管理员协同处理；目标实收前不增加目标库存
          </p>
        </div>
        <div>
          <el-button
            :loading="loading"
            @click="loadData"
          >
            刷新
          </el-button>
          <el-button
            v-permission="'item:transfer:create'"
            type="primary"
            @click="createVisible = true"
          >
            新建移动
          </el-button>
        </div>
      </div>
      <el-table
        v-loading="loading"
        :data="records"
        row-key="id"
        class="table-scroll-x"
      >
        <el-table-column
          label="移动单"
          min-width="190"
        >
          <template #default="{ row }">
            <div class="transfer_panel__document">
              <el-button
                link
                type="primary"
                @click="openDetail(row)"
              >
                {{ row.documentNo }}
              </el-button>
              <span class="t-hint">ID {{ row.id }} · v{{ row.version }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="移动方向"
          min-width="230"
        >
          <template #default="{ row }">
            {{ row.fromLocationName || row.fromLocationId }} → {{ row.toLocationName || row.toLocationId }}
          </template>
        </el-table-column>
        <el-table-column
          label="原经办人"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.operatorName || row.operatorId }}
          </template>
        </el-table-column>
        <el-table-column
          label="发出时间"
          min-width="155"
        >
          <template #default="{ row }">
            {{ formatTime(row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="最近实收"
          min-width="180"
        >
          <template #default="{ row }">
            {{ row.receivedBy || '—' }}<span
              v-if="row.receivedAt"
              class="t-hint transfer_panel__block"
            >{{ formatTime(row.receivedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="120"
        >
          <template #default="{ row }">
            <ItemStatusPill :status="row.transferStatus" />
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="90"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openDetail(row)"
            >
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="transfer_panel__pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="search"
          @current-change="loadData"
        />
      </div>
    </div>

    <TransferDetailDrawer
      ref="detailDrawer"
      v-model="detailVisible"
      :transfer-id="selectedTransferId"
      @changed="loadData"
      @review="openReview"
    />
    <SettlementReviewDialog
      v-model="reviewVisible"
      :document-id="reviewDocumentId"
      @success="onReviewSuccess"
    />

    <el-dialog
      v-model="createVisible"
      title="新建库位移动"
      width="min(900px, 95vw)"
      destroy-on-close
      @closed="resetCreate"
    >
      <el-alert
        title="发出时仅扣减源库位可用量并记在途；目标库位必须由目标管理员后续实收。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form
        label-position="top"
        class="transfer_panel__create_form"
      >
        <div class="transfer_panel__location_grid">
          <el-form-item
            label="源库位"
            required
          >
            <ItemLocationSelect
              v-model="createForm.fromLocationId"
              :clearable="false"
              @change="onCreateSourceChange"
            />
          </el-form-item>
          <el-form-item
            label="目标库位"
            required
          >
            <ItemLocationSelect
              v-model="createForm.toLocationId"
              :clearable="false"
            />
          </el-form-item>
        </div>
        <el-form-item
          label="移动物料类型"
          required
        >
          <el-radio-group
            v-model="createForm.kind"
            @change="resetCreateLines"
          >
            <el-radio-button value="CONSUMABLE">
              耗材行
            </el-radio-button>
            <el-radio-button value="FIXED_ASSET">
              固定资产
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="createForm.kind === 'CONSUMABLE'">
          <div
            v-for="(line, index) in createForm.lines"
            :key="line.key"
            class="transfer_panel__create_line"
          >
            <el-select
              v-model="line.balanceKey"
              filterable
              placeholder="选择物料与源存放位置"
              class="transfer_panel__balance"
              @change="(value) => onBalanceChange(line, value)"
            >
              <el-option
                v-for="balance in balances"
                :key="balanceKey(balance)"
                :label="`${balance.materialName} · ${balance.positionText || '默认位置'} · 可用 ${balance.availableQuantity}${balance.baseUnit || ''}`"
                :value="balanceKey(balance)"
                :disabled="Number(balance.availableQuantity) <= 0"
              />
            </el-select>
            <el-input-number
              v-model="line.quantity"
              :min="0"
              :max="line.maxQuantity"
              :precision="0"
              :step="1"
              controls-position="right"
              placeholder="移动数量"
              class="transfer_panel__quantity"
            />
            <el-button
              link
              type="danger"
              :disabled="createForm.lines.length === 1"
              @click="createForm.lines.splice(index, 1)"
            >
              删除
            </el-button>
          </div>
          <el-button
            link
            type="primary"
            @click="addCreateLine"
          >
            + 添加耗材行
          </el-button>
        </template>
        <el-form-item
          v-else
          label="逐台选择在库固定资产"
          required
        >
          <el-select
            v-model="createForm.assetIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="仅显示源库位当前 IDLE 资产"
            class="transfer_panel__assets"
          >
            <el-option
              v-for="asset in assets"
              :key="asset.id"
              :label="`${asset.assetCode} · ${asset.materialName} · ${asset.positionText || '默认位置'}`"
              :value="String(asset.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="createForm.remark"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="creating"
          @click="submitCreate"
        >
          创建移动单
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import TransferDetailDrawer from '../components/TransferDetailDrawer.vue'
import SettlementReviewDialog from '../components/SettlementReviewDialog.vue'
import { createTransfer, pageTransfers } from '@/api/item/workflow'
import { getAssets, getBalances } from '@/api/item/stock'
import { formatInstant } from '@/utils/datetime'

const emit = defineEmits(['changed'])
const query = reactive({ pageNum: 1, pageSize: 10, transferStatus: '', fromLocationId: '', toLocationId: '' })
const records = ref([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detailDrawer = ref(null)
const selectedTransferId = ref('')
const reviewVisible = ref(false)
const reviewDocumentId = ref('')
const createVisible = ref(false)
const creating = ref(false)
const balances = ref([])
const assets = ref([])
const createForm = reactive({ fromLocationId: '', toLocationId: '', kind: 'CONSUMABLE', lines: [], assetIds: [], remark: '' })
let lineSerial = 0
let inventorySerial = 0

const formatTime = (value) => formatInstant(value) || '—'
const balanceKey = (balance) => `${balance.materialId}|${balance.positionText || ''}`

async function loadData() {
  loading.value = true
  try {
    const result = await pageTransfers({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      transferStatus: query.transferStatus || undefined,
      fromLocationId: query.fromLocationId || undefined,
      toLocationId: query.toLocationId || undefined,
      sortBy: 'occurredAt',
      sortOrder: 'desc'
    })
    records.value = result?.records || []
    total.value = Number(result?.total || 0)
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  return loadData()
}

function reset() {
  Object.assign(query, { pageNum: 1, transferStatus: '', fromLocationId: '', toLocationId: '' })
  return loadData()
}

function openDetail(row) {
  selectedTransferId.value = String(row.id)
  detailVisible.value = true
}

function openReview(id) {
  if (!id) return
  reviewDocumentId.value = String(id)
  reviewVisible.value = true
}

async function onReviewSuccess() {
  await Promise.all([loadData(), detailDrawer.value?.reload()])
  emit('changed')
}

function newLine() {
  return { key: ++lineSerial, balanceKey: '', materialId: '', positionText: '', quantity: 0, maxQuantity: undefined }
}

function resetCreateLines() {
  createForm.lines = [newLine()]
  createForm.assetIds = []
}

function addCreateLine() {
  createForm.lines.push(newLine())
}

function resetCreate() {
  inventorySerial += 1
  Object.assign(createForm, { fromLocationId: '', toLocationId: '', kind: 'CONSUMABLE', assetIds: [], remark: '' })
  balances.value = []
  assets.value = []
  resetCreateLines()
}

async function onCreateSourceChange() {
  resetCreateLines()
  balances.value = []
  assets.value = []
  if (!createForm.fromLocationId) return
  const serial = ++inventorySerial
  try {
    const [balanceResult, assetResult] = await Promise.all([
      getBalances({ pageNum: 1, pageSize: 100, locationId: createForm.fromLocationId, sortBy: 'updateTime', sortOrder: 'desc' }),
      getAssets({ pageNum: 1, pageSize: 100, locationId: createForm.fromLocationId, status: 'IDLE' })
    ])
    if (serial !== inventorySerial) return
    balances.value = balanceResult?.records || []
    assets.value = assetResult?.records || []
  } catch {
    if (serial === inventorySerial) {
      balances.value = []
      assets.value = []
    }
  }
}

function onBalanceChange(line, value) {
  const balance = balances.value.find((item) => balanceKey(item) === value)
  line.materialId = balance ? String(balance.materialId) : ''
  line.positionText = balance?.positionText || ''
  line.maxQuantity = balance ? Number(balance.availableQuantity) : undefined
  line.quantity = 0
}

async function submitCreate() {
  if (!createForm.fromLocationId || !createForm.toLocationId) {
    ElMessage.warning('请选择源库位和目标库位')
    return
  }
  if (String(createForm.fromLocationId) === String(createForm.toLocationId)) {
    ElMessage.warning('源库位与目标库位不能相同')
    return
  }
  let lines
  if (createForm.kind === 'CONSUMABLE') {
    const selected = createForm.lines.filter((line) => line.materialId && Number(line.quantity) > 0)
    if (!selected.length) {
      ElMessage.warning('请至少填写一条耗材移动行')
      return
    }
    if (selected.some((line) => !Number.isInteger(Number(line.quantity)))) {
      ElMessage.warning('移动数量必须为整数')
      return
    }
    if (selected.some((line) => Number(line.quantity) > Number(line.maxQuantity))) {
      ElMessage.warning('移动数量不能超过源 position 可用量')
      return
    }
    if (new Set(selected.map((line) => line.balanceKey)).size !== selected.length) {
      ElMessage.warning('同一物料及 position 只能添加一行')
      return
    }
    lines = selected.map((line) => ({ materialId: String(line.materialId), quantity: line.quantity, positionText: line.positionText || '' }))
  } else {
    if (!createForm.assetIds.length) {
      ElMessage.warning('请至少选择一台固定资产')
      return
    }
    lines = createForm.assetIds.map((assetId) => ({ assetId: String(assetId), quantity: 1 }))
  }
  creating.value = true
  try {
    await createTransfer({
      fromLocationId: String(createForm.fromLocationId),
      toLocationId: String(createForm.toLocationId),
      lines,
      idempotencyKey: `transfer-create-${crypto.randomUUID()}`,
      remark: createForm.remark || undefined
    })
    ElMessage.success('移动单已创建，等待目标库位实收')
    createVisible.value = false
    await loadData()
    emit('changed')
  } finally {
    creating.value = false
  }
}

resetCreateLines()
onMounted(loadData)
defineExpose({ reload: loadData })
</script>

<style scoped>
.transfer_panel__filters { margin-bottom: 16px; }
.transfer_panel__filters :deep(.el-form-item) { margin-bottom: 0; }
.transfer_panel__status { width: 150px; }
.transfer_panel__location { width: 200px; }
.transfer_panel__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.transfer_panel__head h2 { margin: 0 0 5px; }
.transfer_panel__head p { margin: 0; }
.transfer_panel__document { display: flex; flex-direction: column; align-items: flex-start; }
.transfer_panel__block { display: block; }
.transfer_panel__pager { display: flex; justify-content: flex-end; margin-top: 16px; overflow-x: auto; }
.transfer_panel__create_form { margin-top: 16px; }
.transfer_panel__location_grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.transfer_panel__create_line { display: grid; grid-template-columns: minmax(300px, 1fr) 180px 50px; align-items: center; gap: 12px; margin-bottom: 10px; }
.transfer_panel__balance, .transfer_panel__assets { width: 100%; }
.transfer_panel__quantity { width: 100%; }
@media (max-width: 768px) {
  .transfer_panel__filters :deep(.el-form-item) { margin-bottom: 10px; }
  .transfer_panel__location_grid { grid-template-columns: 1fr; gap: 0; }
  .transfer_panel__create_line { grid-template-columns: minmax(250px, 1fr) 150px 50px; overflow-x: auto; }
}
</style>
