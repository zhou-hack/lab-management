<template>
  <div class="settlement_panel">
    <el-alert
      title="已出库物料不在此重复核销：请前往原申领出库单登记归还，并在归还单中处理教学损耗、遗失或损坏。"
      type="info"
      :closable="false"
      show-icon
      class="settlement_panel__notice"
    />
    <div class="card settlement_panel__filters">
      <el-form
        :inline="true"
        @submit.prevent
      >
        <el-form-item label="核销类型">
          <el-select
            v-model="query.documentType"
            clearable
            placeholder="全部核销类型"
            class="settlement_panel__type"
          >
            <el-option
              label="在库报废"
              value="SCRAP"
            />
            <el-option
              label="在库调整"
              value="ADJUST"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="单据号">
          <el-input
            v-model="query.documentNo"
            clearable
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="库位">
          <ItemLocationSelect
            v-model="query.locationId"
            class="settlement_panel__location"
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
      <div class="settlement_panel__head">
        <div>
          <h2 class="t-title">
            在库核销与复核
          </h2>
          <p class="t-sub">
            耗材按存放位置调减，固定资产逐台变更目标状态；批准复核后才过账
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
            v-permission="'item:settlement:create'"
            type="primary"
            @click="createVisible = true"
          >
            登记在库核销
          </el-button>
        </div>
      </div>
      <el-tabs
        v-model="reviewFilter"
        class="settlement_panel__tabs"
      >
        <el-tab-pane
          label="待复核"
          name="PENDING_REVIEW"
        />
        <el-tab-pane
          label="全部单据"
          name="ALL"
        />
        <el-tab-pane
          label="已批准"
          name="APPROVED"
        />
        <el-tab-pane
          label="已驳回"
          name="REJECTED"
        />
      </el-tabs>
      <el-table
        v-loading="loading"
        :data="displayRecords"
        row-key="id"
        class="table-scroll-x"
      >
        <el-table-column
          label="核销单"
          min-width="190"
        >
          <template #default="{ row }">
            <div class="settlement_panel__document">
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
          label="类型"
          width="110"
        >
          <template #default="{ row }">
            {{ documentTypeLabel(row.documentType) }}
          </template>
        </el-table-column>
        <el-table-column
          label="库位"
          min-width="150"
        >
          <template #default="{ row }">
            {{ row.locationName || row.locationId }}
          </template>
        </el-table-column>
        <el-table-column
          label="经办人"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.operatorName || row.operatorId }}
          </template>
        </el-table-column>
        <el-table-column
          label="登记时间"
          min-width="155"
        >
          <template #default="{ row }">
            {{ formatTime(row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="复核状态"
          width="115"
        >
          <template #default="{ row }">
            <ItemStatusPill :status="row.reviewStatus" />
          </template>
        </el-table-column>
        <el-table-column
          label="复核人 / 时间"
          min-width="180"
        >
          <template #default="{ row }">
            {{ row.reviewedBy || '—' }}<span
              v-if="row.reviewedAt"
              class="t-hint settlement_panel__block"
            >{{ formatTime(row.reviewedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="130"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openDetail(row)"
            >
              详情
            </el-button>
            <el-button
              v-if="row.reviewStatus === 'PENDING_REVIEW'"
              v-permission="'item:settlement:review'"
              link
              type="success"
              @click="openReview(row)"
            >
              复核
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="t-hint">{{ reviewFilter === 'PENDING_REVIEW' ? '当前查询页没有待复核核销单，可翻页查看历史登记' : '暂无核销单' }}</span>
        </template>
      </el-table>
      <div class="settlement_panel__pager">
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

    <DocumentDetailDrawer
      v-model="detailVisible"
      :document-id="selectedDocumentId"
    />
    <SettlementReviewDialog
      v-model="reviewVisible"
      :document-id="reviewDocumentId"
      @success="onReviewSuccess"
    />

    <el-dialog
      v-model="createVisible"
      title="登记在库核销"
      width="min(1050px, 96vw)"
      destroy-on-close
      @closed="resetCreate"
    >
      <el-alert
        title="登记后进入待复核，不会立即调减库存；仅可选择当前库位实物可用的耗材，以及空闲或维修中的固定资产。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form
        label-position="top"
        class="settlement_panel__create_form"
      >
        <div class="settlement_panel__create_meta">
          <el-form-item
            label="核销类型"
            required
          >
            <el-radio-group v-model="createForm.documentType">
              <el-radio-button value="SCRAP">
                报废
              </el-radio-button>
              <el-radio-button value="ADJUST">
                调整
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item
            label="实际库位"
            required
          >
            <ItemLocationSelect
              v-model="createForm.locationId"
              :clearable="false"
              @change="onLocationChange"
            />
          </el-form-item>
          <el-form-item
            label="物料类型"
            required
          >
            <el-radio-group
              v-model="createForm.kind"
              @change="resetCreateLines"
            >
              <el-radio-button value="CONSUMABLE">
                耗材调减
              </el-radio-button>
              <el-radio-button value="FIXED_ASSET">
                固定资产
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>

        <div
          v-for="(line, index) in createForm.lines"
          :key="line.key"
          class="settlement_panel__create_line"
        >
          <template v-if="createForm.kind === 'CONSUMABLE'">
            <el-select
              v-model="line.balanceKey"
              filterable
              remote
              :remote-method="onInventorySearch"
              :loading="inventorySearching"
              placeholder="输入耗材名称搜索并选择存放位置"
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
              placeholder="调减数量"
            />
            <span class="t-hint">耗材无需目标状态</span>
          </template>
          <template v-else>
            <el-select
              v-model="line.assetId"
              filterable
              remote
              :remote-method="onInventorySearch"
              :loading="inventorySearching"
              placeholder="输入资产名称搜索并逐台选择"
              @change="() => (line.quantity = line.assetId ? 1 : 0)"
            >
              <el-option
                v-for="asset in assets"
                :key="asset.id"
                :label="`${asset.assetCode} · ${asset.materialName} · ${assetStatusLabel(asset.status)}`"
                :value="String(asset.id)"
              />
            </el-select>
            <el-input-number
              :model-value="line.assetId ? 1 : 0"
              :min="0"
              :precision="0"
              :step="1"
              disabled
              controls-position="right"
            />
            <el-select
              v-model="line.targetAssetStatus"
              :disabled="createForm.documentType === 'SCRAP'"
              placeholder="目标资产状态"
            >
              <el-option
                v-if="createForm.documentType === 'SCRAP'"
                label="已报废"
                value="SCRAPPED"
              />
              <template v-else>
                <el-option
                  label="在库"
                  value="IDLE"
                />
                <el-option
                  label="维修中"
                  value="MAINTENANCE"
                />
                <el-option
                  label="已遗失"
                  value="LOST"
                />
                <el-option
                  label="已报废"
                  value="SCRAPPED"
                />
              </template>
            </el-select>
          </template>
          <el-input
            v-model="line.reason"
            maxlength="512"
            placeholder="必填：本行核销原因"
            class="settlement_panel__reason"
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
          + 添加核销行
        </el-button>
        <el-form-item
          label="单据备注"
          class="settlement_panel__remark"
        >
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
          提交待复核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import DocumentDetailDrawer from '../components/DocumentDetailDrawer.vue'
import SettlementReviewDialog from '../components/SettlementReviewDialog.vue'
import { createAdjust, createScrap, pageDocuments } from '@/api/item/workflow'
import { getAssets, getBalances } from '@/api/item/stock'
import { formatInstant } from '@/utils/datetime'
import { assetStatusLabel, documentTypeLabel } from '@/views/item/components/claim.constants'

const emit = defineEmits(['changed'])
const query = reactive({ pageNum: 1, pageSize: 10, documentType: '', documentNo: '', locationId: '' })
const reviewFilter = ref('PENDING_REVIEW')
const records = ref([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const selectedDocumentId = ref('')
const reviewVisible = ref(false)
const reviewDocumentId = ref('')
const createVisible = ref(false)
const creating = ref(false)
const balances = ref([])
const assets = ref([])
const inventorySearching = ref(false)
const createForm = reactive({ documentType: 'SCRAP', locationId: '', kind: 'CONSUMABLE', lines: [], remark: '' })
let lineSerial = 0
let inventorySerial = 0
let inventorySearchTimer = null
let inventorySearchSerial = 0

const displayRecords = computed(() => reviewFilter.value === 'ALL'
  ? records.value
  : records.value.filter((row) => row.reviewStatus === reviewFilter.value))
const formatTime = (value) => formatInstant(value) || '—'
const balanceKey = (balance) => `${balance.materialId}|${balance.positionText || ''}`

watch(() => createForm.documentType, () => resetCreateLines())

async function loadData() {
  loading.value = true
  try {
    const result = await pageDocuments({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      documentType: query.documentType || undefined,
      businessScene: 'IN_STOCK_SETTLE',
      documentNo: query.documentNo || undefined,
      locationId: query.locationId || undefined,
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
  Object.assign(query, { pageNum: 1, documentType: '', documentNo: '', locationId: '' })
  return loadData()
}

function openDetail(row) {
  selectedDocumentId.value = String(row.id)
  detailVisible.value = true
}

function openReview(row) {
  reviewDocumentId.value = String(row.id)
  reviewVisible.value = true
}

async function onReviewSuccess() {
  await loadData()
  emit('changed')
}

function newLine() {
  return {
    key: ++lineSerial,
    balanceKey: '', materialId: '', positionText: '', maxQuantity: undefined,
    assetId: '', quantity: 0, targetAssetStatus: createForm.documentType === 'SCRAP' ? 'SCRAPPED' : '', reason: ''
  }
}

function resetCreateLines() {
  createForm.lines = [newLine()]
}

function addCreateLine() {
  createForm.lines.push(newLine())
}

function mergeInventoryOptions(kind, freshRecords) {
  const currentRecords = kind === 'CONSUMABLE' ? balances.value : assets.value
  const selectedKeys = new Set(createForm.lines.map((line) => (
    kind === 'CONSUMABLE' ? line.balanceKey : String(line.assetId || '')
  )))
  const keyOf = (item) => kind === 'CONSUMABLE' ? balanceKey(item) : String(item.id)
  const retained = currentRecords.filter((item) => selectedKeys.has(keyOf(item)))
  const fresh = freshRecords.filter((item) => !selectedKeys.has(keyOf(item)))
  return [...retained, ...fresh]
}

function onInventorySearch(keyword = '') {
  window.clearTimeout(inventorySearchTimer)
  const locationId = createForm.locationId
  const kind = createForm.kind
  if (!locationId) return
  inventorySerial += 1
  inventorySearchTimer = window.setTimeout(async () => {
    const serial = ++inventorySearchSerial
    inventorySearching.value = true
    try {
      if (kind === 'CONSUMABLE') {
        const result = await getBalances({
          pageNum: 1,
          pageSize: 20,
          locationId,
          materialName: keyword.trim() || undefined
        })
        if (serial !== inventorySearchSerial || kind !== createForm.kind || locationId !== createForm.locationId) return
        balances.value = mergeInventoryOptions(kind, result?.records || [])
      } else {
        const [idleResult, maintenanceResult] = await Promise.all([
          getAssets({ pageNum: 1, pageSize: 20, locationId, status: 'IDLE', materialName: keyword.trim() || undefined }),
          getAssets({ pageNum: 1, pageSize: 20, locationId, status: 'MAINTENANCE', materialName: keyword.trim() || undefined })
        ])
        if (serial !== inventorySearchSerial || kind !== createForm.kind || locationId !== createForm.locationId) return
        assets.value = mergeInventoryOptions(kind, [
          ...(idleResult?.records || []),
          ...(maintenanceResult?.records || [])
        ])
      }
    } catch {
      if (serial === inventorySearchSerial) {
        if (kind === 'CONSUMABLE') balances.value = mergeInventoryOptions(kind, [])
        else assets.value = mergeInventoryOptions(kind, [])
      }
    } finally {
      if (serial === inventorySearchSerial) inventorySearching.value = false
    }
  }, 300)
}

function resetCreate() {
  inventorySerial += 1
  inventorySearchSerial += 1
  window.clearTimeout(inventorySearchTimer)
  inventorySearching.value = false
  Object.assign(createForm, { documentType: 'SCRAP', locationId: '', kind: 'CONSUMABLE', remark: '' })
  balances.value = []
  assets.value = []
  resetCreateLines()
}

async function onLocationChange() {
  inventorySearchSerial += 1
  window.clearTimeout(inventorySearchTimer)
  inventorySearching.value = false
  resetCreateLines()
  balances.value = []
  assets.value = []
  if (!createForm.locationId) return
  const serial = ++inventorySerial
  try {
    const [balanceResult, idleResult, maintenanceResult] = await Promise.all([
      getBalances({ pageNum: 1, pageSize: 100, locationId: createForm.locationId }),
      getAssets({ pageNum: 1, pageSize: 100, locationId: createForm.locationId, status: 'IDLE' }),
      getAssets({ pageNum: 1, pageSize: 100, locationId: createForm.locationId, status: 'MAINTENANCE' })
    ])
    if (serial !== inventorySerial) return
    balances.value = balanceResult?.records || []
    assets.value = [...(idleResult?.records || []), ...(maintenanceResult?.records || [])]
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
  line.maxQuantity = balance ? Math.min(Number(balance.availableQuantity), Number(balance.ownedQuantity)) : undefined
  line.quantity = 0
}

async function submitCreate() {
  if (!createForm.locationId) {
    ElMessage.warning('请选择实际核销库位')
    return
  }
  const selected = createForm.lines.filter((line) => createForm.kind === 'CONSUMABLE'
    ? line.materialId && Number(line.quantity) > 0
    : line.assetId)
  if (!selected.length) {
    ElMessage.warning('请至少填写一条有效核销行')
    return
  }
  if (selected.some((line) => !line.reason.trim())) {
    ElMessage.warning('每条核销行都必须填写原因')
    return
  }
  let lines
  if (createForm.kind === 'CONSUMABLE') {
    if (selected.some((line) => Number(line.quantity) > Number(line.maxQuantity))) {
      ElMessage.warning('调减数量不能超过该存放位置的可用或保有数量')
      return
    }
    if (new Set(selected.map((line) => line.materialId)).size !== selected.length) {
      ElMessage.warning('同一耗材在一次在库核销中只能出现一次')
      return
    }
    lines = selected.map((line) => ({
      materialId: String(line.materialId), quantity: line.quantity,
      positionText: line.positionText || '', reason: line.reason.trim()
    }))
  } else {
    if (new Set(selected.map((line) => line.assetId)).size !== selected.length) {
      ElMessage.warning('同一台固定资产只能出现一次')
      return
    }
    if (createForm.documentType === 'ADJUST' && selected.some((line) => !line.targetAssetStatus)) {
      ElMessage.warning('固定资产调整必须选择目标状态')
      return
    }
    lines = selected.map((line) => ({
      assetId: String(line.assetId), quantity: 1,
      targetAssetStatus: createForm.documentType === 'SCRAP' ? 'SCRAPPED' : line.targetAssetStatus,
      reason: line.reason.trim()
    }))
  }
  if (lines.some((line) => !Number.isInteger(Number(line.quantity)))) {
    ElMessage.warning('核销数量必须为整数')
    return
  }
  creating.value = true
  try {
    const api = createForm.documentType === 'SCRAP' ? createScrap : createAdjust
    await api({
      locationId: String(createForm.locationId),
      lines,
      idempotencyKey: `stock-settlement-${createForm.documentType}-${crypto.randomUUID()}`,
      remark: createForm.remark || undefined
    })
    ElMessage.success('在库核销已登记，请由其他管理员复核')
    createVisible.value = false
    reviewFilter.value = 'PENDING_REVIEW'
    await search()
    emit('changed')
  } finally {
    creating.value = false
  }
}

resetCreateLines()
onMounted(loadData)
onUnmounted(() => {
  window.clearTimeout(inventorySearchTimer)
  inventorySearchSerial += 1
  inventorySerial += 1
})
defineExpose({ reload: loadData })
</script>

<style scoped>
.settlement_panel__notice { margin-bottom: 16px; }
.settlement_panel__filters { margin-bottom: 16px; }
.settlement_panel__filters :deep(.el-form-item) { margin-bottom: 0; }
.settlement_panel__type { width: 185px; }
.settlement_panel__location { width: 200px; }
.settlement_panel__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.settlement_panel__head h2 { margin: 0 0 5px; }
.settlement_panel__head p { margin: 0; }
.settlement_panel__tabs { margin-top: 8px; }
.settlement_panel__document { display: flex; flex-direction: column; align-items: flex-start; }
.settlement_panel__block { display: block; }
.settlement_panel__pager { display: flex; justify-content: flex-end; margin-top: 16px; overflow-x: auto; }
.settlement_panel__create_form { margin-top: 16px; }
.settlement_panel__create_meta { display: grid; grid-template-columns: 1.1fr 1fr 1fr; gap: 14px; }
.settlement_panel__create_line { display: grid; grid-template-columns: minmax(220px, 1.3fr) 155px minmax(150px, .8fr); gap: 10px; padding: 12px; margin-bottom: 10px; border: .5px solid var(--hair); border-radius: 10px; }
.settlement_panel__reason { grid-column: 1 / 3; }
.settlement_panel__remark { margin-top: 18px; }
@media (max-width: 900px) {
  .settlement_panel__create_meta { grid-template-columns: 1fr; gap: 0; }
  .settlement_panel__create_line { min-width: 720px; }
  .settlement_panel__create_form { overflow-x: auto; }
}
</style>
