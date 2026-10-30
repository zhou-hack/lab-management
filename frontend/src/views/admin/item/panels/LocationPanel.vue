<template>
  <div class="location_panel">
    <div class="card location_panel__card">
      <div class="location_panel__toolbar">
        <el-input
          v-model="query.locationName"
          placeholder="库位名称"
          clearable
          class="location_panel__input"
          @keyup.enter="search"
          @clear="search"
        />
        <el-input
          v-model="query.locationCode"
          placeholder="库位编码"
          clearable
          class="location_panel__input"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-model="query.enabled"
          placeholder="全部状态"
          clearable
          class="location_panel__status"
          @change="search"
        >
          <el-option
            label="启用"
            :value="1"
          />
          <el-option
            label="停用"
            :value="0"
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
        <el-button
          plain
          @click="openChangeHistory"
        >
          变动记录
        </el-button>
        <el-button
          v-if="canManage"
          type="primary"
          plain
          @click="openForm()"
        >
          新建库位
        </el-button>
      </div>

      <div
        v-loading="loading"
        class="location_panel__content"
      >
        <div
          v-if="records.length"
          class="location_panel__grid"
        >
          <el-card
            v-for="location in records"
            :key="location.id"
            class="location_panel__location"
            shadow="never"
            tabindex="0"
            role="button"
            @click="openInventory(location)"
            @keydown.enter="openInventory(location)"
          >
            <div class="location_panel__location_head">
              <h3>{{ location.locationName }}</h3>
              <span
                class="pill"
                :class="location.enabled === 1 ? 'pill--ok' : 'pill--muted'"
              >
                {{ location.enabled === 1 ? '启用' : '停用' }}
              </span>
            </div>
            <div class="location_panel__admins">
              <span class="location_panel__admin_label">负责人</span>
              <div
                v-if="location.admins?.length"
                class="location_panel__admin_list"
              >
                <span
                  v-for="admin in location.admins"
                  :key="admin.userId"
                  class="location_panel__admin"
                >
                  {{ admin.realName }}
                </span>
              </div>
              <span
                v-else
                class="t-hint"
              >暂未分配负责人</span>
            </div>
            <div class="location_panel__foot">
              <span class="t-hint">点击查看库内库存</span>
              <div
                v-if="canManage"
                @click.stop
              >
                <el-button
                  link
                  type="primary"
                  @click="openForm(location)"
                >
                  编辑
                </el-button>
                <el-button
                  link
                  :type="location.enabled === 1 ? 'danger' : 'success'"
                  :loading="togglingId === location.id"
                  @click="toggleLocation(location)"
                >
                  {{ location.enabled === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  :loading="deletingId === location.id"
                  @click="confirmDelete(location)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </el-card>
        </div>
        <el-empty
          v-else-if="!loading"
          description="暂无符合条件的库位"
        />
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="location_panel__pager"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        background
        @size-change="onSizeChange"
        @current-change="loadData"
      />
    </div>

    <el-dialog
      v-model="formVisible"
      :title="editingLocation ? '编辑库位' : '新建库位'"
      width="600px"
      :close-on-click-modal="false"
      @closed="formRef?.clearValidate()"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="96px"
      >
        <el-form-item
          label="库位编码"
          prop="locationCode"
        >
          <el-input
            v-model="form.locationCode"
            maxlength="64"
            placeholder="字母、数字、点、下划线或横线"
          />
        </el-form-item>
        <el-form-item
          label="库位名称"
          prop="locationName"
        >
          <el-input
            v-model="form.locationName"
            maxlength="128"
            placeholder="请输入库位名称"
          />
        </el-form-item>
        <el-form-item
          label="库位说明"
          prop="description"
        >
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
        <el-form-item
          label="负责人"
          prop="userIds"
        >
          <el-select
            v-model="form.userIds"
            :loading="adminLoading"
            multiple
            clearable
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="搜索并选择具体用户，可选择多人"
            class="location_panel__form_control"
          >
            <el-option
              v-for="admin in adminOptions"
              :key="admin.userId"
              :label="`${admin.realName}（${admin.accountName || admin.userId}）`"
              :value="admin.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="启用状态"
          prop="enabled"
        >
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
          />
          <span class="location_panel__switch_text">{{ form.enabled === 1 ? '启用' : '停用' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="inventoryVisible"
      :title="inventory ? `${inventory.locationName} · 库内库存` : '库内库存'"
      size="760px"
    >
      <div
        v-loading="inventoryLoading"
        class="location_panel__inventory"
      >
        <template v-if="inventory">
          <el-input
            v-model="materialNameQuery"
            clearable
            placeholder="搜索物料名称"
            class="location_panel__inventory_search"
          />
          <section class="location_panel__inventory_section">
            <h3 class="sechead">
              库内耗材
            </h3>
            <el-table
              :data="pagedBalances"
              row-key="id"
            >
              <el-table-column
                prop="materialName"
                label="耗材"
                min-width="130"
              />
              <el-table-column
                label="库内位置"
                min-width="110"
              >
                <template #default="{ row }">
                  {{ row.positionText || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="保有"
                min-width="100"
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
                label="借出"
                min-width="100"
                align="right"
              >
                <template #default="{ row }">
                  <item-quantity-text
                    :value="row.loanedQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="实际可用"
                min-width="100"
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
                <span class="t-hint">{{ materialNameKeyword ? '没有匹配的耗材' : '该库位暂无耗材库存' }}</span>
              </template>
            </el-table>
            <el-pagination
              v-model:current-page="balancePage"
              class="location_panel__inventory_pager"
              :page-size="7"
              :total="filteredBalances.length"
              layout="total, prev, pager, next"
              background
            />
          </section>
          <section class="location_panel__inventory_section">
            <h3 class="sechead">
              固定资产物料汇总（保有 / 可用）
            </h3>
            <el-table
              :data="pagedAssetSummary"
              row-key="materialId"
            >
              <el-table-column
                prop="materialName"
                label="固定资产物料"
                min-width="140"
              />
              <el-table-column
                label="规格型号"
                min-width="140"
              >
                <template #default="{ row }">
                  {{ row.specification || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="保有"
                min-width="100"
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
                min-width="100"
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
                <span class="t-hint">{{ materialNameKeyword ? '没有匹配的固定资产汇总' : '该库位暂无固定资产汇总' }}</span>
              </template>
            </el-table>
            <el-pagination
              v-model:current-page="assetSummaryPage"
              class="location_panel__inventory_pager"
              :page-size="7"
              :total="filteredAssetSummary.length"
              layout="total, prev, pager, next"
              background
            />
          </section>
          <section class="location_panel__inventory_section">
            <h3 class="sechead">
              库内固定资产
            </h3>
            <el-table
              :data="pagedAssets"
              row-key="id"
            >
              <el-table-column
                prop="assetCode"
                label="资产编码"
                min-width="130"
              />
              <el-table-column
                prop="materialName"
                label="物料"
                min-width="120"
              />
              <el-table-column
                label="状态"
                width="100"
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
                label="库内位置"
                min-width="110"
              >
                <template #default="{ row }">
                  {{ row.positionText || '—' }}
                </template>
              </el-table-column>
              <template #empty>
                <span class="t-hint">{{ materialNameKeyword ? '没有匹配的固定资产' : '该库位暂无固定资产' }}</span>
              </template>
            </el-table>
            <el-pagination
              v-model:current-page="assetPage"
              class="location_panel__inventory_pager"
              :page-size="7"
              :total="filteredAssets.length"
              layout="total, prev, pager, next"
              background
            />
          </section>
        </template>
        <el-empty
          v-else-if="!inventoryLoading"
          description="未获取到库内库存"
        />
      </div>
    </el-drawer>

    <el-drawer
      v-model="changeHistoryVisible"
      title="库位变动记录"
      size="760px"
    >
      <el-table
        v-loading="changeHistoryLoading"
        :data="changeHistoryRecords"
        row-key="id"
      >
        <el-table-column
          label="库位"
          min-width="180"
        >
          <template #default="{ row }">
            <div>{{ row.locationName || '—' }}</div>
            <div class="t-hint">
              {{ row.locationCode || '' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="变动类型"
          width="100"
        >
          <template #default="{ row }">
            <el-tag :type="row.changeType === 'CREATE' ? 'success' : 'danger'">
              {{ row.changeType === 'CREATE' ? '创建' : '删除' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作人"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.operatorName || row.operatorId || '系统' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作时间"
          min-width="180"
        >
          <template #default="{ row }">
            {{ formatInstant(row.occurredAt, { withSeconds: true }) || '—' }}
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            description="暂无库位创建或删除记录"
            :image-size="64"
          />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="changeHistoryQuery.pageNum"
        v-model:page-size="changeHistoryQuery.pageSize"
        class="location_panel__pager"
        :total="changeHistoryTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @size-change="onChangeHistorySizeChange"
        @current-change="loadChangeHistory"
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import {
  createLocation,
  deleteLocation,
  getLocationAdminOptions,
  getLocationChangeHistory,
  getLocationInventory,
  getLocations,
  updateLocation,
  updateLocationAdmins
} from '@/api/item/stock'
import { useUserStore } from '@/store/user'
import { formatInstant } from '@/utils/datetime'
import { hasPermission } from '@/utils/permission'

const store = useUserStore()
const canManage = computed(() => hasPermission('item:location:manage', store.permissions))

function custodianNames(asset) {
  return (asset?.custodians || [])
    .map((item) => item.realName || item.accountName || item.userId)
    .join('、') || '—'
}

const query = reactive({ locationName: '', locationCode: '', enabled: '', pageNum: 1, pageSize: 12 })
const records = ref([])
const total = ref(0)
const loading = ref(false)
const togglingId = ref('')
const deletingId = ref('')

async function loadData() {
  loading.value = true
  try {
    const result = await getLocations({
      ...query,
      locationName: query.locationName.trim() || undefined,
      locationCode: query.locationCode.trim() || undefined,
      enabled: query.enabled === '' ? undefined : query.enabled
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
  loadData()
}

function reset() {
  Object.assign(query, { locationName: '', locationCode: '', enabled: '', pageNum: 1, pageSize: 12 })
  loadData()
}

function onSizeChange() {
  query.pageNum = 1
  loadData()
}

const formVisible = ref(false)
const formRef = ref()
const editingLocation = ref(null)
const submitting = ref(false)
const adminOptions = ref([])
const adminLoading = ref(false)
const emptyForm = () => ({ locationCode: '', locationName: '', description: '', userIds: [], enabled: 1 })
const form = reactive(emptyForm())
const rules = {
  locationCode: [
    { required: true, whitespace: true, message: '请输入库位编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_.-]+$/, message: '库位编码仅支持字母、数字、点、下划线和横线', trigger: 'blur' }
  ],
  locationName: [{ required: true, whitespace: true, message: '请输入库位名称', trigger: 'blur' }],
  enabled: [{ required: true, message: '请选择启用状态', trigger: 'change' }]
}

async function loadAdminOptions() {
  adminLoading.value = true
  try {
    const result = await getLocationAdminOptions()
    const options = (result || []).map((item) => ({ ...item, userId: String(item.userId) }))
    for (const admin of editingLocation.value?.admins || []) {
      if (!options.some((item) => item.userId === admin.userId)) options.push(admin)
    }
    adminOptions.value = options
  } catch {
    adminOptions.value = editingLocation.value?.admins || []
  } finally {
    adminLoading.value = false
  }
}

function openForm(location = null) {
  editingLocation.value = location
  Object.assign(form, emptyForm(), location ? {
    locationCode: location.locationCode,
    locationName: location.locationName,
    description: location.description || '',
    userIds: (location.admins || []).map((admin) => admin.userId),
    enabled: location.enabled
  } : {})
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
  loadAdminOptions()
}

function locationPayload(source) {
  return {
    locationCode: source.locationCode.trim(),
    locationName: source.locationName.trim(),
    description: source.description?.trim() || null,
    enabled: source.enabled
  }
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = locationPayload(form)
    const saved = editingLocation.value
      ? await updateLocation(editingLocation.value.id, payload)
      : await createLocation(payload)
    await updateLocationAdmins(saved.id, [...form.userIds])
    ElMessage.success('库位已保存')
    formVisible.value = false
    await loadData()
  } catch {
    // 请求层已展示具体错误
  } finally {
    submitting.value = false
  }
}

async function toggleLocation(location) {
  togglingId.value = location.id
  try {
    await updateLocation(location.id, locationPayload({ ...location, enabled: location.enabled === 1 ? 0 : 1 }))
    ElMessage.success(location.enabled === 1 ? '库位已停用' : '库位已启用')
    await loadData()
  } catch {
    // 请求层已展示具体错误
  } finally {
    togglingId.value = ''
  }
}

async function confirmDelete(location) {
  try {
    await ElMessageBox.confirm(
      `确定删除库位“${location.locationName}（${location.locationCode}）”吗？仍有库存或未完成移动的库位不可删除，删除后编码可重新使用。`,
      '删除库位',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deletingId.value = location.id
  try {
    await deleteLocation(location.id)
    ElMessage.success('库位已删除')
    if (records.value.length === 1 && query.pageNum > 1) query.pageNum--
    await loadData()
  } catch {
    // 请求层已展示业务冲突或权限错误
  } finally {
    deletingId.value = ''
  }
}

const inventoryVisible = ref(false)
const inventoryLoading = ref(false)
const inventory = ref(null)
const materialNameQuery = ref('')
const balancePage = ref(1)
const assetSummaryPage = ref(1)
const assetPage = ref(1)

const materialNameKeyword = computed(() => materialNameQuery.value.trim().toLowerCase())
const filteredBalances = computed(() => (inventory.value?.balances || [])
  .filter((item) => String(item.materialName || '').toLowerCase().includes(materialNameKeyword.value)))
const filteredAssets = computed(() => (inventory.value?.assets || [])
  .filter((item) => String(item.materialName || '').toLowerCase().includes(materialNameKeyword.value)))

const filteredAssetSummary = computed(() => {
  const groups = new Map()
  for (const asset of filteredAssets.value) {
    const key = String(asset.materialId || asset.materialName || '')
    if (!groups.has(key)) {
      groups.set(key, {
        materialId: key,
        materialName: asset.materialName,
        specification: asset.specification,
        baseUnit: asset.baseUnit,
        ownedQuantity: 0,
        availableQuantity: 0
      })
    }
    const summary = groups.get(key)
    if (!['LOST', 'SCRAPPED'].includes(asset.status)) summary.ownedQuantity += 1
    if (asset.status === 'IDLE') summary.availableQuantity += 1
  }
  return [...groups.values()].sort((left, right) => (
    String(left.materialName || '').localeCompare(String(right.materialName || ''), 'zh-CN')
  ))
})

const pagedBalances = computed(() => {
  const start = (balancePage.value - 1) * 7
  return filteredBalances.value.slice(start, start + 7)
})

const pagedAssetSummary = computed(() => {
  const start = (assetSummaryPage.value - 1) * 7
  return filteredAssetSummary.value.slice(start, start + 7)
})

const pagedAssets = computed(() => {
  const start = (assetPage.value - 1) * 7
  return filteredAssets.value.slice(start, start + 7)
})

watch(materialNameQuery, () => {
  balancePage.value = 1
  assetSummaryPage.value = 1
  assetPage.value = 1
})

async function openInventory(location) {
  inventoryVisible.value = true
  inventoryLoading.value = true
  inventory.value = null
  materialNameQuery.value = ''
  balancePage.value = 1
  assetSummaryPage.value = 1
  assetPage.value = 1
  try {
    inventory.value = await getLocationInventory(location.id)
  } catch {
    inventory.value = null
  } finally {
    inventoryLoading.value = false
  }
}

const changeHistoryVisible = ref(false)
const changeHistoryLoading = ref(false)
const changeHistoryRecords = ref([])
const changeHistoryTotal = ref(0)
const changeHistoryQuery = reactive({ pageNum: 1, pageSize: 10 })

async function loadChangeHistory() {
  changeHistoryLoading.value = true
  try {
    const result = await getLocationChangeHistory(changeHistoryQuery)
    changeHistoryRecords.value = result?.records || []
    changeHistoryTotal.value = Number(result?.total || 0)
  } catch {
    changeHistoryRecords.value = []
    changeHistoryTotal.value = 0
  } finally {
    changeHistoryLoading.value = false
  }
}

function openChangeHistory() {
  changeHistoryVisible.value = true
  changeHistoryQuery.pageNum = 1
  loadChangeHistory()
}

function onChangeHistorySizeChange() {
  changeHistoryQuery.pageNum = 1
  loadChangeHistory()
}

onMounted(loadData)
</script>

<style scoped>
.location_panel__card {
  padding: 18px;
}

.location_panel__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  overflow-x: auto;
}

.location_panel__toolbar > * {
  flex-shrink: 0;
}

.location_panel__input {
  width: 180px;
}

.location_panel__status {
  width: 120px;
}

.location_panel__content {
  min-height: 180px;
}

.location_panel__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}

.location_panel__location {
  cursor: pointer;
  border-radius: var(--r-md);
  transition: border-color var(--dur-fast) var(--ease);
}

.location_panel__location:hover,
.location_panel__location:focus-visible {
  border-color: var(--brand);
  outline: none;
}

.location_panel__location_head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.location_panel__location_head h3 {
  margin: 0;
  font-size: 16px;
  color: var(--g1);
}

.location_panel__admins {
  min-height: 68px;
  margin: 18px 0;
}

.location_panel__admin_label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--g3);
}

.location_panel__admin_list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.location_panel__admin {
  padding: 4px 9px;
  border-radius: var(--r-full);
  background: var(--brand-a08);
  color: var(--brand);
  font-size: 12px;
}

.location_panel__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 28px;
}

.location_panel__pager {
  justify-content: flex-end;
  margin-top: 18px;
  overflow-x: auto;
}

.location_panel__form_control {
  width: 100%;
}

.location_panel__switch_text {
  margin-left: 10px;
  color: var(--g3);
}

.location_panel__inventory {
  min-height: 220px;
}

.location_panel__inventory_search {
  width: 100%;
  max-width: 320px;
  margin-bottom: 20px;
}

.location_panel__inventory_section + .location_panel__inventory_section {
  margin-top: 28px;
}

.location_panel__inventory_pager {
  justify-content: flex-end;
  margin-top: 12px;
  overflow-x: auto;
}

@media (max-width: 768px) {
  .location_panel__card {
    padding: 14px;
  }

  .location_panel__grid {
    grid-template-columns: 1fr;
  }
}
</style>
