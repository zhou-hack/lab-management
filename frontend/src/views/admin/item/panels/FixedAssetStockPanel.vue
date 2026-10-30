<template>
  <div class="fixed_asset_stock">
    <div class="card fixed_asset_stock__summary_card">
      <div class="sechead">
        物料汇总（保有 / 可用）
      </div>
      <el-table
        v-loading="summaryLoading"
        :data="pagedSummary"
        row-key="materialId"
      >
        <el-table-column
          prop="materialName"
          label="固定资产物料"
          min-width="170"
        />
        <el-table-column
          label="规格型号"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.specification || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="保有"
          min-width="110"
          align="right"
        >
          <template #default="{ row }">
            <item-quantity-text
              :value="row.ownedQuantity"
              :unit="row.baseUnit"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="可用"
          min-width="110"
          align="right"
        >
          <template #default="{ row }">
            <item-quantity-text
              :value="row.availableQuantity"
              :unit="row.baseUnit"
            />
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            description="暂无固定资产汇总"
            :image-size="56"
          />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="summaryPage"
        class="fixed_asset_stock__pager"
        :page-size="5"
        :total="filteredSummary.length"
        layout="total, prev, pager, next"
        background
        small
      />
    </div>

    <div class="card fixed_asset_stock__card">
      <div class="sechead">
        单台资产
      </div>
      <div class="fixed_asset_stock__toolbar">
        <el-input
          v-model="query.materialName"
          placeholder="物料名称"
          clearable
          class="fixed_asset_stock__input"
          @keyup.enter="search"
          @clear="search"
        />
        <el-input
          v-model="query.assetCode"
          placeholder="资产编码"
          clearable
          class="fixed_asset_stock__input"
          @keyup.enter="search"
          @clear="search"
        />
        <item-location-select
          v-model="query.locationId"
          class="fixed_asset_stock__location"
          placeholder="全部库位"
          @change="search"
        />
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          class="fixed_asset_stock__status"
          @change="search"
        >
          <el-option
            v-for="(label, status) in ASSET_STATUS_LABELS"
            :key="status"
            :label="label"
            :value="status"
          />
        </el-select>
        <el-button
          type="primary"
          @click="search"
        >
          查询
        </el-button>
        <el-button @click="reset">
          重置
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        row-key="id"
      >
        <el-table-column
          prop="assetCode"
          label="资产编码"
          min-width="140"
        />
        <el-table-column
          prop="materialName"
          label="物料名称"
          min-width="140"
        />
        <el-table-column
          label="库位"
          min-width="140"
        >
          <template #default="{ row }">
            <div>{{ row.locationName || '—' }}</div>
            <div class="t-hint">
              {{ row.locationCode || '' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="库内位置"
          min-width="120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.positionText || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="105"
        >
          <template #default="{ row }">
            <item-status-pill :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column
          label="负责人"
          min-width="100"
        >
          <template #default="{ row }">
            {{ custodianNames(row) }}
          </template>
        </el-table-column>
        <el-table-column
          label="更新时间"
          min-width="150"
        >
          <template #default="{ row }">
            {{ formatInstant(row.updateTime) || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="280"
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
              v-if="canManageAsset"
              link
              type="primary"
              @click="openAssetDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canManageAsset && canToggleMaintenance(row)"
              link
              :type="row.status === 'MAINTENANCE' ? 'success' : 'warning'"
              :loading="maintenanceLoadingId === row.id"
              @click="toggleMaintenance(row)"
            >
              {{ row.status === 'MAINTENANCE' ? '解除维修' : '维修' }}
            </el-button>
            <el-button
              v-if="canManageLocation"
              link
              type="primary"
              @click="openResponsibleDialog(row)"
            >
              负责人
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            description="暂无符合条件的固定资产"
            :image-size="64"
          />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="fixed_asset_stock__pager"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @size-change="onSizeChange"
        @current-change="loadAssets"
      />
    </div>

    <el-drawer
      v-model="detailVisible"
      title="固定资产详情"
      size="620px"
    >
      <div
        v-loading="detailLoading"
        class="fixed_asset_stock__detail"
      >
        <template v-if="detail">
          <div class="fixed_asset_stock__detail_head">
            <div>
              <h3>{{ detail.materialName }}</h3>
              <p class="t-sub">
                {{ detail.assetCode }}
              </p>
            </div>
            <item-status-pill :status="detail.status" />
          </div>
          <el-descriptions
            :column="2"
            border
          >
            <el-descriptions-item label="资产编码">
              {{ detail.assetCode }}
            </el-descriptions-item>
            <el-descriptions-item label="价格">
              {{ displayPrice(detail.price) }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <item-status-pill :status="detail.status" />
            </el-descriptions-item>
            <el-descriptions-item label="负责人">
              {{ custodianNames(detail) }}
            </el-descriptions-item>
            <el-descriptions-item label="库位">
              {{ [detail.locationName, detail.locationCode].filter(Boolean).join(' · ') || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="库内位置">
              {{ detail.positionText || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="物料名称">
              {{ detail.materialName || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="规格型号">
              {{ detail.specification || '—' }}
            </el-descriptions-item>
            <el-descriptions-item
              label="更新时间"
              :span="2"
            >
              {{ formatInstant(detail.updateTime, { withSeconds: true }) || '—' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty
          v-else-if="!detailLoading"
          description="未获取到资产详情"
        />
      </div>
    </el-drawer>

    <el-dialog
      v-model="assetDialogVisible"
      title="编辑固定资产"
      width="680px"
      :close-on-click-modal="false"
      @closed="assetFormRef?.clearValidate()"
    >
      <el-form
        ref="assetFormRef"
        :model="assetForm"
        :rules="assetRules"
        label-width="110px"
      >
        <el-form-item label="固定资产物料">
          <el-input
            :model-value="editingAsset.materialName"
            disabled
          />
        </el-form-item>
        <el-form-item
          label="资产编码"
          prop="assetCode"
        >
          <el-input
            v-model="assetForm.assetCode"
            maxlength="64"
            placeholder="请输入唯一资产编码"
          />
        </el-form-item>
        <el-form-item
          label="资产单价"
          prop="price"
        >
          <el-input-number
            v-model="assetForm.price"
            :min="0"
            :precision="2"
            :step="100"
            controls-position="right"
            class="fixed_asset_stock__form_control"
            placeholder="选填"
          />
        </el-form-item>
        <el-form-item label="库位">
          <el-input
            :model-value="[editingAsset.locationName, editingAsset.locationCode].filter(Boolean).join(' · ')"
            disabled
          />
          <div class="t-hint fixed_asset_stock__form_hint">
            库位不可编辑，请通过库位移动业务变更
          </div>
        </el-form-item>
        <el-form-item
          label="库内位置"
          prop="positionText"
        >
          <el-input
            v-model="assetForm.positionText"
            maxlength="256"
            placeholder="选填，如 A 区 3 号柜"
          />
        </el-form-item>
        <el-form-item label="资产状态">
          <el-input
            :model-value="assetStatusLabel(editingAsset.status)"
            disabled
          />
          <div class="t-hint fixed_asset_stock__form_hint">
            状态由借出、移动、维修或核销流程变更，不能在此编辑
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assetDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="assetSubmitting"
          @click="submitAsset"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="responsibleDialogVisible"
      title="更改库位负责人"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="固定资产负责人与所在库位保持一致，本次修改将同步影响该库位的全部固定资产。"
        type="warning"
        :closable="false"
        show-icon
        class="fixed_asset_stock__responsible_notice"
      />
      <el-form label-width="100px">
        <el-form-item label="固定资产">
          <el-input
            :model-value="responsibleAsset ? `${responsibleAsset.assetCode} · ${responsibleAsset.materialName}` : ''"
            disabled
          />
        </el-form-item>
        <el-form-item label="所在库位">
          <el-input
            :model-value="responsibleAsset ? [responsibleAsset.locationName, responsibleAsset.locationCode].filter(Boolean).join(' · ') : ''"
            disabled
          />
        </el-form-item>
        <el-form-item label="库位负责人">
          <el-select
            v-model="responsibleUserIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="2"
            :loading="responsibleLoading"
            placeholder="选择一名或多名负责人"
            class="fixed_asset_stock__form_control"
          >
            <el-option
              v-for="option in responsibleOptions"
              :key="option.userId"
              :label="`${option.realName}（${option.accountName || option.userId}）`"
              :value="option.userId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="responsibleDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="responsibleSubmitting"
          @click="submitResponsible"
        >
          保存负责人
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import {
  getAsset,
  getAssets,
  getAssetSummary,
  getLocationAdminOptions,
  toggleAssetMaintenance,
  updateAsset,
  updateLocationAdmins
} from '@/api/item/stock'
import { useUserStore } from '@/store/user'
import { formatInstant } from '@/utils/datetime'
import { hasPermission } from '@/utils/permission'
import { ASSET_STATUS_LABELS, assetStatusLabel } from '@/views/item/components/claim.constants'

const store = useUserStore()
const canManageAsset = computed(() => hasPermission('item:asset:manage', store.permissions))
const canManageLocation = computed(() => hasPermission('item:location:manage', store.permissions))

function custodianNames(asset) {
  return (asset?.custodians || [])
    .map((item) => item.realName || item.accountName || item.userId)
    .join('、') || '—'
}

function canToggleMaintenance(asset) {
  return ['IDLE', 'MAINTENANCE'].includes(asset?.status)
}

const query = reactive({ materialName: '', assetCode: '', locationId: '', status: '', pageNum: 1, pageSize: 10 })
const summaryPage = ref(1)
const records = ref([])
const total = ref(0)
const loading = ref(false)
const summary = ref([])
const summaryLoading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const maintenanceLoadingId = ref('')

const filteredSummary = computed(() => {
  const keyword = query.materialName.trim().toLowerCase()
  if (!keyword) return summary.value
  return summary.value.filter((item) => item.materialName?.toLowerCase().includes(keyword))
})

const pagedSummary = computed(() => {
  const start = (summaryPage.value - 1) * 5
  return filteredSummary.value.slice(start, start + 5)
})

watch(() => query.materialName, () => {
  summaryPage.value = 1
})

async function loadSummary() {
  summaryLoading.value = true
  try {
    summary.value = await getAssetSummary() || []
  } catch {
    summary.value = []
  } finally {
    summaryLoading.value = false
  }
}

async function loadAssets() {
  loading.value = true
  try {
    const result = await getAssets({
      ...query,
      materialName: query.materialName.trim() || undefined,
      assetCode: query.assetCode.trim() || undefined,
      locationId: query.locationId || undefined,
      status: query.status || undefined
    })
    records.value = result?.records || []
    total.value = Number(result?.total || 0)
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadAssets()
}

function reset() {
  Object.assign(query, { materialName: '', assetCode: '', locationId: '', status: '', pageNum: 1, pageSize: 10 })
  loadAssets()
}

function onSizeChange() {
  query.pageNum = 1
  loadAssets()
}

function displayPrice(price) {
  return price === null || price === undefined || price === '' ? '—' : `¥ ${price}`
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getAsset(row.id)
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

const assetDialogVisible = ref(false)
const assetSubmitting = ref(false)
const assetFormRef = ref()
const editingAsset = ref(null)
const responsibleDialogVisible = ref(false)
const responsibleSubmitting = ref(false)
const responsibleLoading = ref(false)
const responsibleAsset = ref(null)
const responsibleOptions = ref([])
const responsibleUserIds = ref([])
const emptyAssetForm = () => ({
  assetCode: '',
  price: null,
  positionText: ''
})
const assetForm = reactive(emptyAssetForm())
const assetRules = {
  assetCode: [{ required: true, whitespace: true, message: '请输入资产编码', trigger: 'blur' }]
}

function openAssetDialog(row) {
  editingAsset.value = row
  Object.assign(assetForm, emptyAssetForm(), {
    assetCode: row.assetCode,
    price: row.price === null ? null : row.price,
    positionText: row.positionText || ''
  })
  assetDialogVisible.value = true
  nextTick(() => assetFormRef.value?.clearValidate())
}

async function openResponsibleDialog(row) {
  responsibleAsset.value = row
  responsibleUserIds.value = (row.custodians || []).map((item) => String(item.userId))
  responsibleDialogVisible.value = true
  responsibleLoading.value = true
  try {
    const result = await getLocationAdminOptions()
    const options = (result || []).map((item) => ({ ...item, userId: String(item.userId) }))
    for (const current of row.custodians || []) {
      if (!options.some((item) => item.userId === String(current.userId))) {
        options.unshift({ ...current, userId: String(current.userId) })
      }
    }
    responsibleOptions.value = options
  } catch {
    responsibleOptions.value = (row.custodians || []).map((item) => ({ ...item, userId: String(item.userId) }))
  } finally {
    responsibleLoading.value = false
  }
}

async function submitResponsible() {
  if (!responsibleAsset.value) return
  responsibleSubmitting.value = true
  try {
    await updateLocationAdmins(responsibleAsset.value.locationId, responsibleUserIds.value)
    ElMessage.success('库位负责人已更新，同库位固定资产已同步')
    responsibleDialogVisible.value = false
    await loadAssets()
  } catch {
    // 请求层已展示具体错误
  } finally {
    responsibleSubmitting.value = false
  }
}

async function submitAsset() {
  try {
    await assetFormRef.value.validate()
  } catch {
    return
  }
  assetSubmitting.value = true
  try {
    await updateAsset(editingAsset.value.id, {
      assetCode: assetForm.assetCode.trim(),
      price: assetForm.price,
      positionText: assetForm.positionText.trim() || null
    })
    ElMessage.success('固定资产已保存')
    assetDialogVisible.value = false
    await Promise.all([loadAssets(), loadSummary()])
  } catch {
    // 请求层已展示具体错误
  } finally {
    assetSubmitting.value = false
  }
}

async function toggleMaintenance(asset) {
  maintenanceLoadingId.value = asset.id
  try {
    const saved = await toggleAssetMaintenance(asset.id)
    ElMessage.success(saved.status === 'MAINTENANCE' ? '资产已进入维修状态' : '资产已解除维修状态')
    await Promise.all([loadAssets(), loadSummary()])
  } catch {
    // 请求层已展示具体错误
  } finally {
    maintenanceLoadingId.value = ''
  }
}

onMounted(() => {
  loadSummary()
  loadAssets()
})
</script>

<style scoped>
.fixed_asset_stock__summary_card,
.fixed_asset_stock__card {
  padding: 18px;
}

.fixed_asset_stock__summary_card {
  margin-bottom: 14px;
}

.fixed_asset_stock__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  overflow-x: auto;
}

.fixed_asset_stock__toolbar > * {
  flex-shrink: 0;
}

.fixed_asset_stock__input,
.fixed_asset_stock__location {
  width: 180px;
}

.fixed_asset_stock__status {
  width: 175px;
}

.fixed_asset_stock__pager {
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

.fixed_asset_stock__form_control {
  width: 100%;
}

.fixed_asset_stock__form_hint {
  width: 100%;
  line-height: 1.5;
}

.fixed_asset_stock__detail {
  min-height: 180px;
}

.fixed_asset_stock__detail_head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.fixed_asset_stock__detail_head h3 {
  margin: 0 0 5px;
  color: var(--g1);
}

.fixed_asset_stock__detail_head p {
  margin: 0;
}

.fixed_asset_stock__responsible_notice {
  margin-bottom: 18px;
}

@media (max-width: 768px) {
  .fixed_asset_stock__summary_card,
  .fixed_asset_stock__card {
    padding: 14px;
  }

  .fixed_asset_stock :deep(.el-drawer) {
    width: 100vw !important;
  }

  .fixed_asset_stock :deep(.el-dialog) {
    width: calc(100vw - 28px) !important;
  }

  .fixed_asset_stock__detail :deep(.el-descriptions__body .el-descriptions__table) {
    table-layout: fixed;
  }
}
</style>
