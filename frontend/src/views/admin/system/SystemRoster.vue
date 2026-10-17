<template>
  <div class="roster_page">
    <!-- 筛选栏 -->
    <el-card class="roster_page__card">
      <div class="roster_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="学工号 / 姓名 / 手机号"
              clearable
              style="width: 210px"
              @keyup.enter="search"
              @clear="search"
            />
          </el-form-item>
          <el-form-item label="身份">
            <el-select
              v-model="query.personType"
              placeholder="全部"
              clearable
              style="width: 110px"
              @change="search"
            >
              <el-option
                label="学生"
                value="STUDENT"
              />
              <el-option
                label="老师"
                value="TEACHER"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="建档状态">
            <el-select
              v-model="query.matched"
              placeholder="全部"
              clearable
              style="width: 120px"
              @change="search"
            >
              <el-option
                label="已建档"
                :value="1"
              />
              <el-option
                label="未建档"
                :value="0"
              />
            </el-select>
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
        <div>
          <el-button
            v-permission="'system:roster:export'"
            @click="doExport"
          >
            导出 CSV
          </el-button>
          <el-button
            v-permission="'system:roster:import'"
            @click="openImport"
          >
            导入 CSV
          </el-button>
          <el-button
            v-permission="'system:roster:create'"
            @click="openBatch"
          >
            批量粘贴
          </el-button>
          <el-button
            v-permission="'system:roster:create'"
            type="primary"
            @click="openCreate"
          >
            录入名单
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 名单表格 -->
    <el-card class="roster_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          prop="personNo"
          label="学工号"
          min-width="120"
        />
        <el-table-column
          prop="realName"
          label="姓名"
          min-width="110"
        />
        <el-table-column
          label="身份"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.personType === 'TEACHER' ? 'primary' : 'success'"
              size="small"
            >
              {{ row.personType === 'TEACHER' ? '老师' : '学生' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="phone"
          label="手机号"
          min-width="120"
        />
        <el-table-column
          prop="departmentName"
          label="部门"
          min-width="130"
        />
        <el-table-column
          label="状态"
          width="150"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.matched"
              type="info"
              size="small"
            >
              已建档
            </el-tag>
            <el-tag
              v-else-if="row.enabled"
              type="success"
              size="small"
            >
              待建档
            </el-tag>
            <el-tag
              v-else
              type="warning"
              size="small"
            >
              已停用
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="录入时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="130"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'system:roster:update'"
              link
              type="primary"
              :disabled="row.matched"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'system:roster:enable'"
              link
              :type="row.enabled ? 'danger' : 'success'"
              :disabled="row.matched"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无名单" />
        </template>
      </el-table>
      <div class="roster_page__pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 单条录入 -->
    <el-dialog
      v-model="createVisible"
      title="录入名单"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="86px"
      >
        <el-form-item
          label="学工号"
          prop="personNo"
        >
          <el-input
            v-model="createForm.personNo"
            placeholder="如 S20260003"
          />
        </el-form-item>
        <el-form-item
          label="身份"
          prop="personType"
        >
          <el-radio-group v-model="createForm.personType">
            <el-radio value="STUDENT">
              学生
            </el-radio>
            <el-radio value="TEACHER">
              老师
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="姓名"
          prop="realName"
        >
          <el-input v-model="createForm.realName" />
        </el-form-item>
        <el-form-item
          label="手机号"
          prop="phone"
        >
          <el-input
            v-model="createForm.phone"
            maxlength="11"
          />
        </el-form-item>
        <el-form-item
          label="所属部门"
          prop="departmentId"
        >
          <el-select
            v-model="createForm.departmentId"
            placeholder="选择部门"
            style="width: 100%"
          >
            <el-option
              v-for="d in departments"
              :key="d.id"
              :label="d.departmentName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="roster_page__tip t-hint">
        本人凭手机号接收验证码首登，系统按名单自动建档并赋予对应身份。
      </p>
      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCreate"
        >
          录入
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog
      v-model="batchVisible"
      title="批量导入名单"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="batchFormRef"
        :model="batchForm"
        :rules="batchRules"
        label-width="86px"
      >
        <el-form-item
          label="身份"
          prop="personType"
        >
          <el-radio-group v-model="batchForm.personType">
            <el-radio value="STUDENT">
              学生
            </el-radio>
            <el-radio value="TEACHER">
              老师
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="所属部门"
          prop="departmentId"
        >
          <el-select
            v-model="batchForm.departmentId"
            placeholder="本批次统一部门"
            style="width: 100%"
          >
            <el-option
              v-for="d in departments"
              :key="d.id"
              :label="d.departmentName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="名单内容"
          prop="text"
        >
          <el-input
            v-model="batchForm.text"
            type="textarea"
            :rows="8"
            placeholder="每行一条：学工号,姓名,手机号&#10;例如：&#10;S20260003,张三,13800000003&#10;S20260004,李四,13800000004"
          />
        </el-form-item>
      </el-form>
      <p class="roster_page__tip t-hint">
        单次最多 200 条；任一条学工号或手机号与现有名单重复时，整批不入库。
      </p>
      <template #footer>
        <el-button @click="batchVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitBatch"
        >
          导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑 -->
    <el-dialog
      v-model="editVisible"
      title="编辑名单"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="86px"
      >
        <el-form-item label="学工号">
          <el-input
            :model-value="editRow?.personNo"
            disabled
          />
        </el-form-item>
        <el-form-item
          label="姓名"
          prop="realName"
        >
          <el-input v-model="editForm.realName" />
        </el-form-item>
        <el-form-item
          label="手机号"
          prop="phone"
        >
          <el-input
            v-model="editForm.phone"
            maxlength="11"
          />
        </el-form-item>
        <el-form-item
          label="所属部门"
          prop="departmentId"
        >
          <el-select
            v-model="editForm.departmentId"
            style="width: 100%"
          >
            <el-option
              v-for="d in departments"
              :key="d.id"
              :label="d.departmentName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitEdit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入 CSV -->
    <el-dialog
      v-model="importVisible"
      title="导入名单 CSV"
      width="520px"
      destroy-on-close
    >
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".csv"
        :on-change="onImportFileChange"
        :on-remove="onImportFileRemove"
        :on-exceed="onImportFileExceed"
      >
        <el-icon size="28">
          <UploadFilled />
        </el-icon>
        <div class="el-upload__text">
          拖拽 CSV 文件到此处，或 <em>点击选择</em>
        </div>
      </el-upload>
      <p class="roster_page__tip t-hint">
        仅支持 UTF-8 编码 CSV（≤2MB、≤1000 行），列：学工号,身份类型,姓名,手机号,部门编码；
        重复或非法的行自动跳过并给出失败明细，其余正常入库。
        <el-link
          type="primary"
          @click="doDownloadTemplate"
        >
          下载导入模板
        </el-link>
      </p>
      <template #footer>
        <el-button @click="importVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :disabled="!importFile"
          :loading="submitting"
          @click="submitImport"
        >
          导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入结果 -->
    <el-dialog
      v-model="importResultVisible"
      title="导入结果"
      width="560px"
    >
      <p class="roster_page__result">
        共 {{ importResult.totalCount }} 行：成功
        <span class="roster_page__ok">{{ importResult.successCount }}</span> 行，失败
        <span class="roster_page__fail">{{ importResult.failCount }}</span> 行
      </p>
      <el-table
        v-if="importResult.errors.length"
        :data="importResult.errors"
        max-height="320"
        size="small"
      >
        <el-table-column
          prop="rowIndex"
          label="行号"
          width="70"
        />
        <el-table-column
          prop="personNo"
          label="学工号"
          width="130"
        />
        <el-table-column
          prop="reason"
          label="失败原因"
          min-width="220"
        />
      </el-table>
      <p
        v-if="importResult.failCount > importResult.errors.length"
        class="roster_page__tip t-hint"
      >
        失败明细仅显示前 {{ importResult.errors.length }} 条。
      </p>
      <template #footer>
        <el-button
          type="primary"
          @click="importResultVisible = false"
        >
          知道了
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  createRoster,
  createRosterBatch,
  downloadRosterImportTemplate,
  exportRosters,
  importRosters,
  pageRosters,
  updateRoster,
  updateRosterEnabled
} from '@/api/system/roster'
import { getDepartmentOptions } from '@/api/system/user'
import { formatInstant } from '@/utils/datetime'
import { withSafeAuth } from '@/utils/safe_auth'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', personType: '', matched: null })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)
const departments = ref([])

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.keyword) params.keyword = undefined
    if (!params.personType) params.personType = undefined
    if (params.matched === null || params.matched === '') params.matched = undefined
    const data = await pageRosters(params)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  query.personType = ''
  query.matched = null
  search()
}

// ---------- 单条录入 ----------
const createVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ personNo: '', personType: 'STUDENT', realName: '', phone: '', departmentId: null })

const phoneRule = { pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }

const createRules = {
  personNo: [
    { required: true, message: '请输入学工号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{2,32}$/, message: '2-32 位字母或数字', trigger: 'blur' }
  ],
  personType: [{ required: true, message: '请选择身份', trigger: 'change' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, phoneRule],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }]
}

function openCreate() {
  createForm.personNo = ''
  createForm.personType = 'STUDENT'
  createForm.realName = ''
  createForm.phone = ''
  createForm.departmentId = null
  createVisible.value = true
}

async function submitCreate() {
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await createRoster({ ...createForm })
    ElMessage.success('已录入')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

// ---------- 批量导入 ----------
const batchVisible = ref(false)
const batchFormRef = ref()
const batchForm = reactive({ personType: 'STUDENT', departmentId: null, text: '' })

const batchRules = {
  personType: [{ required: true, message: '请选择身份', trigger: 'change' }],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }],
  text: [{ required: true, message: '请粘贴名单内容', trigger: 'blur' }]
}

function openBatch() {
  batchForm.personType = 'STUDENT'
  batchForm.departmentId = null
  batchForm.text = ''
  batchVisible.value = true
}

async function submitBatch() {
  try {
    await batchFormRef.value.validate()
  } catch {
    return
  }
  const items = []
  const lines = batchForm.text.split('\n').map((l) => l.trim()).filter(Boolean)
  for (let i = 0; i < lines.length; i++) {
    const parts = lines[i].split(/[,，]/).map((p) => p.trim())
    if (parts.length !== 3 || !parts[0] || !parts[1] || !parts[2]) {
      ElMessage.error(`第 ${i + 1} 行格式错误，应为：学工号,姓名,手机号`)
      return
    }
    if (!/^[A-Za-z0-9]{2,32}$/.test(parts[0])) {
      ElMessage.error(`第 ${i + 1} 行学工号格式错误`)
      return
    }
    if (!/^1\d{10}$/.test(parts[2])) {
      ElMessage.error(`第 ${i + 1} 行手机号格式错误`)
      return
    }
    items.push({ personNo: parts[0], realName: parts[1], phone: parts[2] })
  }
  if (items.length > 200) {
    ElMessage.error('单次最多导入 200 条')
    return
  }
  submitting.value = true
  try {
    const count = await createRosterBatch({
      personType: batchForm.personType,
      departmentId: batchForm.departmentId,
      items
    })
    ElMessage.success(`已导入 ${count} 条`)
    batchVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

// ---------- 编辑 ----------
const editVisible = ref(false)
const editFormRef = ref()
const editRow = ref(null)
const editForm = reactive({ realName: '', phone: '', departmentId: null })

const editRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, phoneRule],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }]
}

function openEdit(row) {
  editRow.value = row
  editForm.realName = row.realName
  editForm.phone = row.phone
  editForm.departmentId = row.departmentId
  editVisible.value = true
}

async function submitEdit() {
  try {
    await editFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await updateRoster(editRow.value.id, { ...editForm })
    ElMessage.success('已保存')
    editVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

// ---------- 启用/停用 ----------
async function toggleEnabled(row) {
  const disabling = row.enabled === 1
  try {
    await ElMessageBox.confirm(
      disabling
        ? `停用后「${row.realName}」将无法通过首登匹配建档，确定停用吗？`
        : `确定重新启用「${row.realName}」的名单吗？`,
      '提示',
      { type: 'warning', confirmButtonText: disabling ? '停用' : '启用' }
    )
  } catch {
    return
  }
  await updateRosterEnabled(row.id, disabling ? 0 : 1)
  ElMessage.success(disabling ? '已停用' : '已启用')
  load()
}

// ---------- 导出 CSV / 导入 CSV ----------
function saveBlob(blob, filenamePrefix) {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const ts = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${filenamePrefix}_${ts}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

async function doExport() {
  const params = {}
  if (query.keyword) params.keyword = query.keyword
  if (query.personType) params.personType = query.personType
  if (query.matched === 0 || query.matched === 1) params.matched = query.matched
  const blob = await exportRosters(params)
  saveBlob(blob, 'rosters')
  ElMessage.success('已导出')
}

async function doDownloadTemplate() {
  const blob = await downloadRosterImportTemplate()
  saveBlob(blob, 'roster_import_template')
}

const importVisible = ref(false)
const uploadRef = ref()
const importFile = ref(null)
const importResultVisible = ref(false)
const importResult = ref({ totalCount: 0, successCount: 0, failCount: 0, errors: [] })

function openImport() {
  importFile.value = null
  importVisible.value = true
}

function onImportFileChange(file) {
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 2MB')
    uploadRef.value.clearFiles()
    importFile.value = null
    return
  }
  importFile.value = file.raw
}

function onImportFileRemove() {
  importFile.value = null
}

function onImportFileExceed(files) {
  uploadRef.value.clearFiles()
  // handleStart 内部会触发 on-change，大小校验由 onImportFileChange 统一处理
  uploadRef.value.handleStart(files[0])
}

async function submitImport() {
  if (!importFile.value) return
  submitting.value = true
  try {
    const { ok, data } = await withSafeAuth(() => importRosters(importFile.value))
    if (!ok) return
    importVisible.value = false
    importResult.value = data
    importResultVisible.value = true
    if (data.successCount > 0) load()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  load()
  getDepartmentOptions().then((data) => {
    departments.value = data
  })
})
</script>

<style scoped>
.roster_page__card + .roster_page__card {
  margin-top: 14px;
}

.roster_page__filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.roster_page__filter :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}

.roster_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.roster_page__tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
}

.roster_page__result {
  margin: 0 0 12px;
  font-size: 14px;
}

.roster_page__ok {
  color: var(--el-color-success);
  font-weight: 600;
}

.roster_page__fail {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
