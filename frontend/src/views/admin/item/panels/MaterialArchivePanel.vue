<template>
  <div class="material_archive">
    <el-tabs
      v-model="archiveTab"
      class="material_archive__tabs"
    >
      <el-tab-pane
        label="物料档案"
        name="materials"
      >
        <div class="card material_archive__card">
          <div class="material_archive__toolbar">
            <el-input
              v-model="materialQuery.materialName"
              placeholder="物料名称"
              clearable
              class="material_archive__keyword"
              @keyup.enter="searchMaterials"
              @clear="searchMaterials"
            />
            <el-select
              v-model="materialQuery.materialType"
              placeholder="全部类型"
              clearable
              class="material_archive__type"
              @change="searchMaterials"
            >
              <el-option
                label="耗材"
                value="CONSUMABLE"
              />
              <el-option
                label="固定资产"
                value="FIXED_ASSET"
              />
            </el-select>
            <el-select
              v-model="materialQuery.enabled"
              placeholder="全部状态"
              clearable
              class="material_archive__status"
              @change="searchMaterials"
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
              @click="searchMaterials"
            >
              查询
            </el-button>
            <el-button @click="resetMaterials">
              重置
            </el-button>
            <el-button
              v-if="canManageMaterial"
              type="primary"
              plain
              @click="openMaterialDialog()"
            >
              新建物料
            </el-button>
          </div>

          <el-table
            v-loading="materialLoading"
            :data="materialRecords"
            row-key="id"
          >
            <el-table-column
              prop="materialName"
              label="物料名称"
              min-width="150"
            />
            <el-table-column
              label="类型 / 跟踪"
              min-width="150"
            >
              <template #default="{ row }">
                {{ MATERIAL_TYPE_LABELS[row.materialType] || row.materialType }} /
                {{ TRACK_MODE_LABELS[row.trackMode] || row.trackMode }}
              </template>
            </el-table-column>
            <el-table-column
              label="SKU编码"
              min-width="130"
            >
              <template #default="{ row }">
                {{ row.skuCode || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              label="规格型号"
              min-width="140"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.specification || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              prop="baseUnit"
              label="单位"
              width="80"
            />
            <el-table-column
              label="数量模式"
              width="100"
            >
              <template #default="{ row }">
                {{ QUANTITY_MODE_LABELS[row.quantityMode] || row.quantityMode }}
              </template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="90"
            >
              <template #default="{ row }">
                <span
                  class="pill"
                  :class="row.enabled === 1 ? 'pill--ok' : 'pill--muted'"
                >
                  {{ row.enabled === 1 ? '启用' : '停用' }}
                </span>
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
              v-if="canManageMaterial"
              label="操作"
              width="190"
              fixed="right"
            >
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  @click="openMaterialDialog(row)"
                >
                  编辑
                </el-button>
                <el-button
                  link
                  :type="row.enabled === 1 ? 'danger' : 'success'"
                  :loading="togglingMaterialId === row.id"
                  @click="toggleMaterial(row)"
                >
                  {{ row.enabled === 1 ? '停用' : '启用' }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  :loading="deletingMaterialId === row.id"
                  @click="confirmDeleteMaterial(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty
                description="暂无符合条件的物料档案"
                :image-size="64"
              />
            </template>
          </el-table>
          <el-pagination
            v-model:current-page="materialQuery.pageNum"
            v-model:page-size="materialQuery.pageSize"
            class="material_archive__pager"
            :total="materialTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @size-change="onMaterialSizeChange"
            @current-change="loadMaterials"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="materialDialogVisible"
      :title="editingMaterial ? '编辑物料' : '新建物料'"
      width="600px"
      :close-on-click-modal="false"
      @closed="materialFormRef?.clearValidate()"
    >
      <el-form
        ref="materialFormRef"
        :model="materialForm"
        :rules="materialRules"
        label-width="96px"
      >
        <el-form-item
          label="物料类型"
          prop="materialType"
        >
          <el-radio-group
            v-model="materialForm.materialType"
            :disabled="!!editingMaterial"
            @change="onMaterialTypeChange"
          >
            <el-radio-button value="CONSUMABLE">
              耗材
            </el-radio-button>
            <el-radio-button value="FIXED_ASSET">
              固定资产
            </el-radio-button>
          </el-radio-group>
          <div class="t-hint material_archive__form_hint">
            物料类型与跟踪方式建档后不可修改
          </div>
        </el-form-item>
        <el-form-item
          label="跟踪方式"
          prop="trackMode"
        >
          <el-input
            :model-value="`${materialForm.trackMode}（${TRACK_MODE_LABELS[materialForm.trackMode]}）`"
            disabled
          />
        </el-form-item>
        <el-form-item
          label="物料名称"
          prop="materialName"
        >
          <el-input
            v-model="materialForm.materialName"
            maxlength="128"
            placeholder="请输入物料名称"
          />
        </el-form-item>
        <el-form-item
          v-if="materialForm.materialType === 'FIXED_ASSET'"
          label="SKU编码"
          prop="skuCode"
        >
          <el-input
            v-model="materialForm.skuCode"
            maxlength="64"
            placeholder="可空；字母、数字、点、下划线或横线"
          />
        </el-form-item>
        <el-form-item
          label="规格型号"
          prop="specification"
        >
          <el-input
            v-model="materialForm.specification"
            maxlength="256"
            placeholder="选填"
          />
        </el-form-item>
        <el-form-item
          label="基本单位"
          prop="baseUnit"
        >
          <el-input
            v-model="materialForm.baseUnit"
            maxlength="32"
            placeholder="如 个、台、箱"
          />
        </el-form-item>
        <el-form-item
          label="数量模式"
          prop="quantityMode"
        >
          <el-select
            v-model="materialForm.quantityMode"
            class="material_archive__form_control"
          >
            <el-option
              label="精确数量（EXACT）"
              value="EXACT"
            />
            <el-option
              label="估算数量（ESTIMATE）"
              value="ESTIMATE"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="启用状态"
          prop="enabled"
        >
          <el-switch
            v-model="materialForm.enabled"
            :active-value="1"
            :inactive-value="0"
          />
          <span class="material_archive__switch_text">{{ materialForm.enabled === 1 ? '启用' : '停用' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="materialDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="materialSubmitting"
          @click="submitMaterial"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createMaterial,
  deleteMaterial,
  getMaterials,
  updateMaterial
} from '@/api/item/stock'
import { useUserStore } from '@/store/user'
import { formatInstant } from '@/utils/datetime'
import { hasPermission } from '@/utils/permission'
import { materialPayload } from '../inventory.rules'

const MATERIAL_TYPE_LABELS = { CONSUMABLE: '耗材', FIXED_ASSET: '固定资产' }
const TRACK_MODE_LABELS = { QUANTITY: '数量跟踪', SERIAL: '单台序列化' }
const QUANTITY_MODE_LABELS = { EXACT: '精确', ESTIMATE: '估算' }

const store = useUserStore()
const canManageMaterial = computed(() => hasPermission('item:material:manage', store.permissions))
const archiveTab = ref('materials')

const materialQuery = reactive({ materialName: '', materialType: '', enabled: '', pageNum: 1, pageSize: 10 })
const materialRecords = ref([])
const materialTotal = ref(0)
const materialLoading = ref(false)
const togglingMaterialId = ref('')
const deletingMaterialId = ref('')

function cleanParams(query) {
  return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== '' && value !== null && value !== undefined))
}

async function loadMaterials() {
  materialLoading.value = true
  try {
    const result = await getMaterials(cleanParams(materialQuery))
    materialRecords.value = result?.records || []
    materialTotal.value = Number(result?.total || 0)
  } catch {
    materialRecords.value = []
    materialTotal.value = 0
  } finally {
    materialLoading.value = false
  }
}

function searchMaterials() {
  materialQuery.pageNum = 1
  loadMaterials()
}

function resetMaterials() {
  Object.assign(materialQuery, { materialName: '', materialType: '', enabled: '', pageNum: 1, pageSize: 10 })
  loadMaterials()
}

function onMaterialSizeChange() {
  materialQuery.pageNum = 1
  loadMaterials()
}

const materialDialogVisible = ref(false)
const materialSubmitting = ref(false)
const materialFormRef = ref()
const editingMaterial = ref(null)
const emptyMaterialForm = () => ({
  materialType: 'CONSUMABLE',
  trackMode: 'QUANTITY',
  skuCode: '',
  materialName: '',
  specification: '',
  baseUnit: '',
  quantityMode: 'EXACT',
  enabled: 1
})
const materialForm = reactive(emptyMaterialForm())
const materialRules = {
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }],
  trackMode: [{ required: true, message: '跟踪方式不能为空', trigger: 'change' }],
  materialName: [{ required: true, whitespace: true, message: '请输入物料名称', trigger: 'blur' }],
  skuCode: [{ pattern: /^[A-Za-z0-9_.-]*$/, message: 'SKU编码仅支持字母、数字、点、下划线和横线', trigger: 'blur' }],
  baseUnit: [{ required: true, whitespace: true, message: '请输入基本单位', trigger: 'blur' }],
  quantityMode: [{ required: true, message: '请选择数量模式', trigger: 'change' }],
  enabled: [{ required: true, message: '请选择启用状态', trigger: 'change' }]
}

function onMaterialTypeChange(type) {
  materialForm.trackMode = type === 'FIXED_ASSET' ? 'SERIAL' : 'QUANTITY'
}

function openMaterialDialog(row = null) {
  editingMaterial.value = row
  Object.assign(materialForm, emptyMaterialForm(), row ? {
    materialType: row.materialType,
    trackMode: row.trackMode,
    skuCode: row.skuCode || '',
    materialName: row.materialName,
    specification: row.specification || '',
    baseUnit: row.baseUnit,
    quantityMode: row.quantityMode,
    enabled: row.enabled
  } : {})
  materialDialogVisible.value = true
  nextTick(() => materialFormRef.value?.clearValidate())
}

async function submitMaterial() {
  try {
    await materialFormRef.value.validate()
  } catch {
    return
  }
  materialSubmitting.value = true
  try {
    const payload = materialPayload(materialForm)
    if (editingMaterial.value) {
      await updateMaterial(editingMaterial.value.id, payload)
    } else {
      await createMaterial(payload)
    }
    ElMessage.success('物料档案已保存')
    materialDialogVisible.value = false
    await loadMaterials()
  } catch {
    // 请求层已展示具体错误
  } finally {
    materialSubmitting.value = false
  }
}

async function toggleMaterial(row) {
  togglingMaterialId.value = row.id
  try {
    await updateMaterial(row.id, materialPayload({ ...row, enabled: row.enabled === 1 ? 0 : 1 }))
    ElMessage.success(row.enabled === 1 ? '物料已停用' : '物料已启用')
    await loadMaterials()
  } catch {
    // 请求层已展示具体错误
  } finally {
    togglingMaterialId.value = ''
  }
}

async function confirmDeleteMaterial(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除物料“${row.materialName}”吗？删除后不可恢复，SKU编码不可复用；当前库存仍包含该物料（含借出、维修、在途）时将拒绝删除。`,
      '删除物料',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deletingMaterialId.value = row.id
  try {
    await deleteMaterial(row.id)
    ElMessage.success('物料档案已删除')
    if (materialRecords.value.length === 1 && materialQuery.pageNum > 1) materialQuery.pageNum--
    await loadMaterials()
  } catch {
    // 请求层已展示库存冲突或权限错误
  } finally {
    deletingMaterialId.value = ''
  }
}

onMounted(loadMaterials)
</script>

<style scoped>
.material_archive__card {
  padding: 18px;
}

.material_archive__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  overflow-x: auto;
}

.material_archive__toolbar > * {
  flex-shrink: 0;
}

.material_archive__keyword {
  width: 180px;
}

.material_archive__type {
  width: 140px;
}

.material_archive__status {
  width: 120px;
}

.material_archive__pager {
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

.material_archive__form_control {
  width: 100%;
}

.material_archive__form_hint {
  width: 100%;
  line-height: 1.5;
}

.material_archive__switch_text {
  margin-left: 10px;
  color: var(--g3);
}

@media (max-width: 768px) {
  .material_archive__card {
    padding: 14px;
  }

  .material_archive :deep(.el-dialog) {
    width: calc(100vw - 28px) !important;
  }
}
</style>
