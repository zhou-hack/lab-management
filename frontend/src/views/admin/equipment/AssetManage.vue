<template>
  <div class="asset_manage">
    <div class="card">
      <div class="asset_manage__toolbar">
        <el-input
          v-model="query.keyword"
          class="asset_manage__filter"
          placeholder="资产编号 / 序列号 / 名称"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select
          v-model="query.labId"
          class="asset_manage__filter"
          placeholder="全部实验室"
          clearable
          filterable
          @change="search"
        >
          <el-option
            v-for="lab in labOptions"
            :key="lab.id"
            :label="lab.labName"
            :value="lab.id"
          />
        </el-select>
        <el-select
          v-model="query.status"
          class="asset_manage__filter asset_manage__filter--status"
          placeholder="全部状态"
          clearable
          @change="search"
        >
          <el-option
            v-for="option in EQUIPMENT_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button @click="search">
          查询
        </el-button>
        <el-button
          v-permission="'equipment:asset:create'"
          type="primary"
          class="asset_manage__toolbar_action"
          @click="openCreate"
        >
          新增台账
        </el-button>
      </div>
      <div class="table-scroll-x">
        <el-table
          v-loading="loading"
          :data="records"
        >
          <el-table-column
            prop="assetCode"
            label="资产编号"
            min-width="110"
          />
          <el-table-column
            prop="equipmentName"
            label="设备名称"
            min-width="150"
          />
          <el-table-column
            label="序列号"
            min-width="110"
          >
            <template #default="{ row }">
              {{ row.serialNo || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="modelCode"
            label="型号码"
            min-width="100"
          />
          <el-table-column
            prop="labName"
            label="保管实验室"
            min-width="130"
          />
          <el-table-column
            prop="departmentName"
            label="归口部门"
            min-width="110"
          />
          <el-table-column
            label="存放位置"
            min-width="130"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.locationDetail || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="保管人"
            width="90"
          >
            <template #default="{ row }">
              {{ row.custodianName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="购置日期"
            width="110"
          >
            <template #default="{ row }">
              {{ row.purchaseDate || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="价值分级"
            width="90"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="valueLevelMeta(row).pill"
              >
                {{ valueLevelMeta(row).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="90"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="enumMeta(row.status, EQUIPMENT_STATUS).pill"
              >
                {{ enumMeta(row.status, EQUIPMENT_STATUS).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="启用"
            width="80"
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
            label="操作"
            width="210"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-permission="'equipment:asset:update'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-permission="'equipment:asset:transfer'"
                link
                type="primary"
                :disabled="row.status !== 'IDLE'"
                @click="openTransfer(row)"
              >
                迁移
              </el-button>
              <el-button
                v-permission="'equipment:asset:classify'"
                link
                type="primary"
                @click="openClassify(row)"
              >
                定级
              </el-button>
              <el-button
                v-permission="'equipment:asset:delete'"
                link
                type="danger"
                @click="removeAsset(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="asset_manage__pager"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </div>

    <!-- 新增台账 -->
    <el-dialog
      v-model="createDialog.visible"
      title="新增台账"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="100px"
      >
        <el-form-item
          label="设备型号"
          prop="modelId"
        >
          <el-select
            v-model="createForm.modelId"
            filterable
            placeholder="请选择型号"
          >
            <el-option
              v-for="model in modelOptions"
              :key="model.id"
              :label="`${model.modelCode} · ${model.equipmentName}`"
              :value="model.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="资产编号"
          prop="assetCode"
        >
          <el-input
            v-model="createForm.assetCode"
            maxlength="64"
            placeholder="如 ZC-2026-001（创建后不可改）"
          />
        </el-form-item>
        <el-form-item label="序列号">
          <el-input
            v-model="createForm.serialNo"
            maxlength="128"
            placeholder="选填"
          />
        </el-form-item>
        <el-form-item
          label="保管实验室"
          prop="labId"
        >
          <el-select
            v-model="createForm.labId"
            filterable
            placeholder="请选择实验室"
            @change="onCreateLabChange"
          >
            <el-option
              v-for="lab in labOptions"
              :key="lab.id"
              :label="`${lab.labName}（${lab.labCode}）`"
              :value="lab.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="归口部门"
          prop="departmentId"
        >
          <el-tree-select
            v-model="createForm.departmentId"
            :data="departmentOptions"
            node-key="id"
            check-strictly
            placeholder="选择实验室后自动带出，可调整"
          />
        </el-form-item>
        <el-form-item label="存放位置">
          <el-input
            v-model="createForm.locationDetail"
            maxlength="255"
            placeholder="如 3-301-A 柜"
          />
        </el-form-item>
        <el-form-item label="保管人">
          <el-select
            v-model="createForm.custodianId"
            filterable
            clearable
            placeholder="选填"
          >
            <el-option
              v-for="custodian in custodianList"
              :key="custodian.userId"
              :label="custodian.realName"
              :value="custodian.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="购置日期">
          <el-date-picker
            v-model="createForm.purchaseDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
        <el-form-item label="购置价格">
          <el-input-number
            v-model="createForm.purchasePrice"
            :min="0"
            :precision="2"
          />
        </el-form-item>
        <el-form-item label="保修截止">
          <el-date-picker
            v-model="createForm.warrantyUntil"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="createDialog.saving"
          @click="saveCreate"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑台账 -->
    <el-dialog
      v-model="editDialog.visible"
      title="编辑台账"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <el-form-item label="存放位置">
          <el-input
            v-model="editForm.locationDetail"
            maxlength="255"
            placeholder="如 3-301-A 柜"
          />
        </el-form-item>
        <el-form-item label="保管人">
          <el-select
            v-model="editForm.custodianId"
            filterable
            clearable
            placeholder="选填"
          >
            <el-option
              v-for="custodian in custodianList"
              :key="custodian.userId"
              :label="custodian.realName"
              :value="custodian.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="购置日期">
          <el-date-picker
            v-model="editForm.purchaseDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
        <el-form-item label="购置价格">
          <el-input-number
            v-model="editForm.purchasePrice"
            :min="0"
            :precision="2"
          />
        </el-form-item>
        <el-form-item label="保修截止">
          <el-date-picker
            v-model="editForm.warrantyUntil"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch
            v-model="editForm.enabled"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="editDialog.saving"
          @click="saveEdit"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 迁移登记 -->
    <el-dialog
      v-model="transferDialog.visible"
      :title="`迁移登记${transferForm.assetCode ? `（${transferForm.assetCode}）` : ''}`"
      width="520px"
      destroy-on-close
    >
      <el-alert
        title="仅空闲（IDLE）设备可迁移；迁移将登记历史轨迹"
        type="info"
        :closable="false"
        class="asset_manage__alert"
      />
      <el-form
        ref="transferFormRef"
        :model="transferForm"
        :rules="transferRules"
        label-width="100px"
      >
        <el-form-item
          label="目标实验室"
          prop="toLabId"
        >
          <el-select
            v-model="transferForm.toLabId"
            filterable
            placeholder="请选择目标实验室"
          >
            <el-option
              v-for="lab in labOptions"
              :key="lab.id"
              :label="`${lab.labName}（${lab.labCode}）`"
              :value="lab.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标位置">
          <el-input
            v-model="transferForm.toLocation"
            maxlength="255"
            placeholder="选填"
          />
        </el-form-item>
        <el-form-item label="迁移原因">
          <el-input
            v-model="transferForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="transferDialog.saving"
          @click="saveTransfer"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 价值定级 -->
    <el-dialog
      v-model="classifyDialog.visible"
      :title="`价值定级${classifyForm.assetCode ? `（${classifyForm.assetCode}）` : ''}`"
      width="520px"
      destroy-on-close
    >
      <el-alert
        title="定级后记录定级人与时间"
        type="info"
        :closable="false"
        class="asset_manage__alert"
      />
      <el-form
        ref="classifyFormRef"
        :model="classifyForm"
        :rules="classifyRules"
        label-width="100px"
      >
        <el-form-item
          label="价值分级"
          prop="valueLevel"
        >
          <el-radio-group v-model="classifyForm.valueLevel">
            <el-radio value="NORMAL">
              普通
            </el-radio>
            <el-radio value="VALUABLE">
              贵重
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="定级理由">
          <el-input
            v-model="classifyForm.classificationReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classifyDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="classifyDialog.saving"
          @click="saveClassify"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  classifyAsset,
  createAsset,
  custodianOptions,
  deleteAsset,
  pageAssets,
  pageModels,
  transferAsset,
  updateAsset
} from '@/api/equipment'
import { listSpaceOptions } from '@/api/space'
import { departmentTreeOptions } from '@/api/department'
import { EQUIPMENT_STATUS, EQUIPMENT_STATUS_OPTIONS, VALUE_LEVEL, enumMeta } from '@/utils/enums'

/* ---------------- 下拉选项 ---------------- */

const labOptions = ref([])
const modelOptions = ref([])
const custodianList = ref([])
const departmentOptions = ref([])

async function loadLabOptions() {
  try {
    labOptions.value = (await listSpaceOptions()) || []
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

async function loadModelOptions() {
  try {
    const data = await pageModels({ pageNum: 1, pageSize: 100 })
    modelOptions.value = data.records || []
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

async function loadCustodianList() {
  try {
    custodianList.value = (await custodianOptions()) || []
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

async function loadDepartmentOptions() {
  try {
    departmentOptions.value = await departmentTreeOptions()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/* ---------------- 列表 ---------------- */

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', labId: '', status: '' })
const records = ref([])
const total = ref(0)
const loading = ref(false)

function valueLevelMeta(row) {
  return row.valueLevel
    ? enumMeta(row.valueLevel, VALUE_LEVEL)
    : { label: '待分类', pill: 'pill--danger' }
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.keyword.trim()) {
      params.keyword = query.keyword.trim()
    }
    if (query.labId) {
      params.labId = query.labId
    }
    if (query.status) {
      params.status = query.status
    }
    const data = await pageAssets(params)
    records.value = data.records || []
    total.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

/* ---------------- 新增 ---------------- */

const createDialog = reactive({ visible: false, saving: false })
const createFormRef = ref()
const createForm = reactive({
  modelId: '',
  assetCode: '',
  serialNo: '',
  labId: '',
  departmentId: '',
  locationDetail: '',
  custodianId: '',
  purchaseDate: '',
  purchasePrice: null,
  warrantyUntil: ''
})
const createRules = {
  modelId: [{ required: true, message: '请选择设备型号', trigger: 'change' }],
  assetCode: [{ required: true, message: '请输入资产编号', trigger: 'blur' }],
  labId: [{ required: true, message: '请选择保管实验室', trigger: 'change' }],
  departmentId: [{ required: true, message: '请选择归口部门', trigger: 'change' }]
}

function openCreate() {
  Object.assign(createForm, {
    modelId: '',
    assetCode: '',
    serialNo: '',
    labId: '',
    departmentId: '',
    locationDetail: '',
    custodianId: '',
    purchaseDate: '',
    purchasePrice: null,
    warrantyUntil: ''
  })
  createDialog.visible = true
}

/** 选择保管实验室后按实验室归口部门自动带出，减少手工选择 */
function onCreateLabChange(labId) {
  const lab = labOptions.value.find((item) => item.id === labId)
  createForm.departmentId = lab ? lab.departmentId : ''
}

async function saveCreate() {
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  createDialog.saving = true
  try {
    await createAsset({
      modelId: createForm.modelId,
      assetCode: createForm.assetCode.trim(),
      serialNo: createForm.serialNo.trim() || null,
      labId: createForm.labId,
      departmentId: createForm.departmentId,
      locationDetail: createForm.locationDetail.trim() || null,
      custodianId: createForm.custodianId || null,
      purchaseDate: createForm.purchaseDate || null,
      purchasePrice: createForm.purchasePrice ?? null,
      warrantyUntil: createForm.warrantyUntil || null
    })
    ElMessage.success('新增成功')
    createDialog.visible = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    createDialog.saving = false
  }
}

/* ---------------- 编辑 ---------------- */

const editDialog = reactive({ visible: false, saving: false })
const editFormRef = ref()
const editForm = reactive({
  id: '',
  locationDetail: '',
  custodianId: '',
  purchaseDate: '',
  purchasePrice: null,
  warrantyUntil: '',
  enabled: 1
})
const editRules = {
  enabled: [{ required: true, message: '请选择启用状态', trigger: 'change' }]
}

function openEdit(row) {
  Object.assign(editForm, {
    id: row.id,
    locationDetail: row.locationDetail || '',
    custodianId: row.custodianId || '',
    purchaseDate: row.purchaseDate || '',
    purchasePrice: row.purchasePrice,
    warrantyUntil: row.warrantyUntil || '',
    enabled: row.enabled
  })
  editDialog.visible = true
}

async function saveEdit() {
  try {
    await editFormRef.value.validate()
  } catch {
    return
  }
  editDialog.saving = true
  try {
    await updateAsset(editForm.id, {
      locationDetail: editForm.locationDetail.trim() || null,
      custodianId: editForm.custodianId || null,
      purchaseDate: editForm.purchaseDate || null,
      purchasePrice: editForm.purchasePrice ?? null,
      warrantyUntil: editForm.warrantyUntil || null,
      enabled: editForm.enabled
    })
    ElMessage.success('保存成功')
    editDialog.visible = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    editDialog.saving = false
  }
}

/* ---------------- 迁移 ---------------- */

const transferDialog = reactive({ visible: false, saving: false })
const transferFormRef = ref()
const transferForm = reactive({ assetId: '', assetCode: '', toLabId: '', toLocation: '', reason: '' })
const transferRules = {
  toLabId: [{ required: true, message: '请选择目标实验室', trigger: 'change' }]
}

function openTransfer(row) {
  Object.assign(transferForm, {
    assetId: row.id,
    assetCode: row.assetCode,
    toLabId: '',
    toLocation: '',
    reason: ''
  })
  transferDialog.visible = true
}

async function saveTransfer() {
  try {
    await transferFormRef.value.validate()
  } catch {
    return
  }
  transferDialog.saving = true
  try {
    await transferAsset(transferForm.assetId, {
      toLabId: transferForm.toLabId,
      toLocation: transferForm.toLocation.trim() || null,
      reason: transferForm.reason.trim() || null
    })
    ElMessage.success('迁移成功')
    transferDialog.visible = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    transferDialog.saving = false
  }
}

/* ---------------- 定级 ---------------- */

const classifyDialog = reactive({ visible: false, saving: false })
const classifyFormRef = ref()
const classifyForm = reactive({ assetId: '', assetCode: '', valueLevel: 'NORMAL', classificationReason: '' })
const classifyRules = {
  valueLevel: [{ required: true, message: '请选择价值分级', trigger: 'change' }]
}

function openClassify(row) {
  Object.assign(classifyForm, {
    assetId: row.id,
    assetCode: row.assetCode,
    valueLevel: row.valueLevel || 'NORMAL',
    classificationReason: ''
  })
  classifyDialog.visible = true
}

async function saveClassify() {
  try {
    await classifyFormRef.value.validate()
  } catch {
    return
  }
  classifyDialog.saving = true
  try {
    await classifyAsset(classifyForm.assetId, {
      valueLevel: classifyForm.valueLevel,
      classificationReason: classifyForm.classificationReason.trim() || null
    })
    ElMessage.success('定级成功')
    classifyDialog.visible = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    classifyDialog.saving = false
  }
}

/* ---------------- 删除 ---------------- */

async function removeAsset(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除台账「${row.equipmentName}（${row.assetCode}）」吗？删除后资产编号不可复用。`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteAsset(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

onMounted(() => {
  load()
  loadLabOptions()
  loadModelOptions()
  loadCustodianList()
  loadDepartmentOptions()
})
</script>

<style scoped>
.asset_manage__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.asset_manage__filter {
  width: 200px;
}

.asset_manage__filter--status {
  width: 140px;
}

/* 提高选择器特异性以覆盖 .el-button + .el-button 的相邻 margin */
.asset_manage__toolbar .asset_manage__toolbar_action {
  margin-left: auto;
}

.asset_manage__pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.asset_manage__alert {
  margin-bottom: 16px;
}
</style>
