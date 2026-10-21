<template>
  <div class="person_page">
    <!-- 筛选栏 -->
    <el-card class="person_page__card">
      <div class="person_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="学工号 / 姓名 / 手机号 / 账号"
              clearable
              style="width: 220px"
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
              <el-option
                label="管理员"
                value="ADMIN"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="部门">
            <el-select
              v-model="query.departmentId"
              placeholder="全部"
              clearable
              filterable
              style="width: 170px"
              @change="search"
            >
              <el-option
                v-for="d in deptOptions"
                :key="d.id"
                :label="d.departmentName"
                :value="d.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="query.status"
              placeholder="全部"
              clearable
              style="width: 120px"
              @change="search"
            >
              <el-option
                label="未建档"
                value="UNFILED"
              />
              <el-option
                label="正常"
                value="NORMAL"
              />
              <el-option
                label="停用"
                value="DISABLED"
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
            <el-button @click="resetQuery">
              重置
            </el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="person_page__toolbar">
        <el-button
          v-if="canCreatePerson"
          type="primary"
          @click="openPersonCreate"
        >
          新增人员
        </el-button>
        <el-button
          v-permission="'system:roster:import'"
          @click="openImport"
        >
          导入 CSV
        </el-button>
        <el-button
          v-permission="'system:roster:export'"
          @click="doExport"
        >
          导出 CSV
        </el-button>
      </div>
    </el-card>

    <!-- 统一人员表格 -->
    <el-card class="person_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          label="学工号"
          min-width="110"
        >
          <template #default="{ row }">
            {{ row.personNo || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="realName"
          label="姓名"
          min-width="100"
        />
        <el-table-column
          label="身份"
          width="90"
        >
          <template #default="{ row }">
            <el-tag :type="PERSON_TYPE_TAG[row.personType] || 'info'">
              {{ PERSON_TYPE_LABELS[row.personType] || row.personType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="手机号"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.phone || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="部门"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.departmentName || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="登录账号"
          min-width="110"
        >
          <template #default="{ row }">
            {{ row.accountName || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="角色"
          min-width="140"
        >
          <template #default="{ row }">
            <template v-if="roleCodes(row).length">
              <el-tag
                v-for="r in roleCodes(row)"
                :key="r"
                :type="ROLE_TAG[r] || 'info'"
                class="person_page__role_tag"
                size="small"
              >
                {{ ROLE_LABELS[r] || r }}
              </el-tag>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status]?.type || 'info'">
              {{ STATUS_META[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="建档时间"
          width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="330"
          fixed="right"
        >
          <template #default="{ row }">
            <template v-if="!row.userId">
              <el-button
                v-permission="'system:roster:update'"
                link
                type="primary"
                @click="openRosterEdit(row)"
              >
                编辑名单
              </el-button>
              <el-button
                v-permission="'system:roster:enable'"
                link
                :type="row.status === 'ROSTER_DISABLED' ? 'success' : 'danger'"
                @click="toggleRoster(row)"
              >
                {{ row.status === 'ROSTER_DISABLED' ? '启用名单' : '停用名单' }}
              </el-button>
            </template>
            <template v-else>
              <el-button
                v-permission="'system:user:update'"
                link
                type="primary"
                @click="openUserEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-permission="'system:user:enable'"
                link
                :type="row.status === 'USER_DISABLED' ? 'success' : 'danger'"
                @click="toggleUser(row)"
              >
                {{ row.status === 'USER_DISABLED' ? '启用' : '停用' }}
              </el-button>
              <el-button
                v-permission="'system:user:reset_password'"
                link
                type="warning"
                @click="openReset(row)"
              >
                重置密码
              </el-button>
              <el-button
                v-if="row.personType === 'TEACHER'"
                v-permission="'system:user:bind_role'"
                link
                type="primary"
                @click="toggleAdminRole(row)"
              >
                {{ roleCodes(row).includes('ADMIN') ? '解绑管理员' : '绑定管理员' }}
              </el-button>
            </template>
            <el-button
              link
              type="info"
              @click="openDetail(row)"
            >
              详情
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无人员数据" />
        </template>
      </el-table>
      <div class="person_page__pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="search"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 新增人员（学生/老师录入名单，管理员创建账号） -->
    <el-dialog
      v-model="personCreateVisible"
      title="新增人员"
      width="420px"
      destroy-on-close
    >
      <p class="person_page__hint">
        学生/老师录入名单，首登匹配学工号+手机号自动建档；管理员直接创建可登录账号。
      </p>
      <el-form
        ref="personCreateFormRef"
        :model="personCreateForm"
        :rules="personCreateRules"
        label-width="86px"
      >
        <el-form-item
          label="身份"
          prop="personType"
        >
          <el-radio-group
            v-model="personCreateForm.personType"
            @change="onPersonTypeChange"
          >
            <template v-if="canRosterCreate">
              <el-radio value="STUDENT">
                学生
              </el-radio>
              <el-radio value="TEACHER">
                老师
              </el-radio>
            </template>
            <el-radio
              v-if="canUserCreate"
              value="ADMIN"
            >
              管理员
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="personCreateForm.personType !== 'ADMIN'"
          label="学工号"
          prop="personNo"
        >
          <el-input
            v-model.trim="personCreateForm.personNo"
            placeholder="如 S20260003"
          />
        </el-form-item>
        <el-form-item
          v-else
          label="登录账号"
          prop="accountName"
        >
          <el-input
            v-model.trim="personCreateForm.accountName"
            placeholder="字母开头，3-32 位字母数字下划线"
          />
        </el-form-item>
        <el-form-item
          label="姓名"
          prop="realName"
        >
          <el-input v-model.trim="personCreateForm.realName" />
        </el-form-item>
        <el-form-item
          v-if="personCreateForm.personType !== 'ADMIN'"
          label="手机号"
          prop="phone"
        >
          <el-input
            v-model.trim="personCreateForm.phone"
            maxlength="11"
          />
        </el-form-item>
        <el-form-item
          v-else
          label="初始密码"
          prop="password"
        >
          <el-input
            v-model="personCreateForm.password"
            type="password"
            show-password
            placeholder="至少 8 位，含字母和数字"
          />
        </el-form-item>
        <el-form-item
          label="所属部门"
          prop="departmentId"
        >
          <el-select
            v-model="personCreateForm.departmentId"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="d in deptOptions"
              :key="d.id"
              :label="d.departmentName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="personCreateVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitPersonCreate"
        >
          {{ personCreateForm.personType === 'ADMIN' ? '创建' : '录入' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑名单 -->
    <el-dialog
      v-model="rosterEditVisible"
      title="编辑名单"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="rosterEditFormRef"
        :model="rosterEditForm"
        :rules="rosterEditRules"
        label-width="86px"
      >
        <el-form-item label="学工号">
          <el-input
            :model-value="rosterEditRow?.personNo"
            disabled
          />
        </el-form-item>
        <el-form-item label="身份">
          <el-input
            :model-value="PERSON_TYPE_LABELS[rosterEditRow?.personType] || rosterEditRow?.personType"
            disabled
          />
        </el-form-item>
        <el-form-item
          label="姓名"
          prop="realName"
        >
          <el-input v-model.trim="rosterEditForm.realName" />
        </el-form-item>
        <el-form-item
          label="手机号"
          prop="phone"
        >
          <el-input
            v-model.trim="rosterEditForm.phone"
            maxlength="11"
          />
        </el-form-item>
        <el-form-item
          label="所属部门"
          prop="departmentId"
        >
          <el-select
            v-model="rosterEditForm.departmentId"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="d in deptOptions"
              :key="d.id"
              :label="d.departmentName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rosterEditVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitRosterEdit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户 -->
    <el-dialog
      v-model="userEditVisible"
      title="编辑用户"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="userEditFormRef"
        :model="userEditForm"
        :rules="userEditRules"
        label-width="86px"
      >
        <el-form-item label="账号">
          <el-input
            :model-value="userEditRow?.accountName || userEditRow?.personNo"
            disabled
          />
        </el-form-item>
        <el-form-item
          label="姓名"
          prop="realName"
        >
          <el-input v-model.trim="userEditForm.realName" />
        </el-form-item>
        <el-form-item
          label="手机号"
          prop="phone"
        >
          <el-input
            v-model.trim="userEditForm.phone"
            maxlength="11"
            placeholder="留空则不设置"
          />
        </el-form-item>
        <el-form-item
          label="所属部门"
          prop="departmentId"
        >
          <el-select
            v-model="userEditForm.departmentId"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="d in deptOptions"
              :key="d.id"
              :label="d.departmentName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userEditVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitUserEdit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog
      v-model="resetVisible"
      title="重置密码"
      width="420px"
      destroy-on-close
    >
      <p class="person_page__hint">
        重置后该用户须重新登录并强制修改密码；该操作需二次密码确认。
      </p>
      <el-form
        ref="resetFormRef"
        :model="resetForm"
        :rules="resetRules"
        label-width="86px"
      >
        <el-form-item label="账号">
          <el-input
            :model-value="resetRow?.accountName || resetRow?.personNo"
            disabled
          />
        </el-form-item>
        <el-form-item
          label="新密码"
          prop="newPassword"
        >
          <el-input
            v-model="resetForm.newPassword"
            type="password"
            show-password
            placeholder="至少 8 位，含字母和数字"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitReset"
        >
          重置
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入 CSV -->
    <el-dialog
      v-model="importVisible"
      title="批量导入名单"
      width="520px"
      destroy-on-close
    >
      <p class="person_page__hint">
        请使用 UTF-8 编码的 CSV，列为：学工号,身份类型,姓名,手机号,部门编码；身份填 STUDENT/TEACHER 或 学生/老师；系统导出的名单文件可直接再导入（多余列自动忽略）。部分行失败不影响其他行入库。该操作需二次密码确认。
      </p>
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".csv"
        :on-change="onImportFileChange"
        :on-exceed="onImportFileExceed"
        :on-remove="onImportFileRemove"
      >
        <div class="person_page__upload_tip">
          拖拽 CSV 文件到此处，或 <em>点击选择</em>
        </div>
      </el-upload>
      <template #footer>
        <el-button @click="downloadTemplate">
          下载模板
        </el-button>
        <el-button @click="importVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!importFile"
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
      destroy-on-close
    >
      <template v-if="importResult">
        <p class="person_page__hint">
          共 {{ importResult.total }} 行：
          <span class="person_page__ok">成功 {{ importResult.success }} 行</span>，
          <span :class="importResult.failed ? 'person_page__fail' : ''">失败 {{ importResult.failed }} 行</span>
        </p>
        <template v-if="importResult.errors?.length">
          <el-table
            :data="importResult.errors"
            max-height="320"
          >
            <el-table-column
              prop="rowIndex"
              label="行号"
              width="80"
            />
            <el-table-column
              prop="personNo"
              label="学工号"
              width="130"
            />
            <el-table-column
              prop="reason"
              label="失败原因"
            />
          </el-table>
          <p class="person_page__hint">
            失败明细仅显示前 {{ importResult.errors.length }} 条。
          </p>
        </template>
      </template>
    </el-dialog>

    <!-- 人员详情 -->
    <el-dialog
      v-model="detailVisible"
      title="人员详情"
      width="520px"
      destroy-on-close
    >
      <el-descriptions
        v-if="detailRow"
        :column="2"
        border
      >
        <el-descriptions-item label="学工号">
          {{ detailRow.personNo || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="姓名">
          {{ detailRow.realName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="身份">
          {{ PERSON_TYPE_LABELS[detailRow.personType] || detailRow.personType }}
        </el-descriptions-item>
        <el-descriptions-item label="手机号">
          {{ detailRow.phone || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="部门">
          {{ detailRow.departmentName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="登录账号">
          {{ detailRow.accountName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="角色">
          {{ roleCodes(detailRow).map(r => ROLE_LABELS[r] || r).join('、') || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ STATUS_META[detailRow.status]?.label || detailRow.status }}
        </el-descriptions-item>
        <el-descriptions-item label="建档时间">
          {{ formatInstant(detailRow.createTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pagePersons } from '@/api/system/person'
import {
  createUser,
  getDepartmentOptions,
  resetUserPassword,
  updateUser,
  updateUserAdminRole,
  updateUserEnabled
} from '@/api/system/user'
import {
  createRoster,
  downloadRosterImportTemplate,
  exportRosters,
  importRosters,
  updateRoster,
  updateRosterEnabled
} from '@/api/system/roster'
import { withSafeAuth } from '@/utils/safe_auth'
import { hasPermission } from '@/utils/permission'
import { useUserStore } from '@/store/user'
import { formatInstant } from '@/utils/datetime'

const PERSON_TYPE_LABELS = { STUDENT: '学生', TEACHER: '老师', ADMIN: '管理员' }
const PERSON_TYPE_TAG = { STUDENT: 'success', TEACHER: 'primary', ADMIN: 'warning' }
const ROLE_LABELS = { ADMIN: '管理员', TEACHER: '老师', STUDENT: '学生', ASSISTANT: '老师助理', OPS_ADMIN: '运维管理员' }
const ROLE_TAG = { ADMIN: 'warning', OPS_ADMIN: 'danger', TEACHER: 'primary', STUDENT: 'success', ASSISTANT: 'info' }
const STATUS_META = {
  UNFILED: { label: '未建档', type: 'info' },
  NORMAL: { label: '正常', type: 'success' },
  ROSTER_DISABLED: { label: '名单停用', type: 'warning' },
  USER_DISABLED: { label: '账号停用', type: 'danger' }
}
const PHONE_PATTERN = /^1\d{10}$/
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/

const query = reactive({ keyword: '', personType: '', departmentId: null, status: '' })
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const submitting = ref(false)
const deptOptions = ref([])

function roleCodes(row) {
  return (row?.roles || '').split(',').filter(Boolean)
}

function buildParams(withPage = true) {
  const p = {}
  if (withPage) {
    p.pageNum = pageNum.value
    p.pageSize = pageSize.value
  }
  if (query.keyword) p.keyword = query.keyword
  if (query.personType) p.personType = query.personType
  if (query.departmentId) p.departmentId = query.departmentId
  if (query.status) p.status = query.status
  return p
}

async function load() {
  loading.value = true
  try {
    const data = await pagePersons(buildParams())
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

function resetQuery() {
  query.keyword = ''
  query.personType = ''
  query.departmentId = null
  query.status = ''
  search()
}

function saveBlob(blob, prefix) {
  const url = URL.createObjectURL(blob)
  const t = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const a = document.createElement('a')
  a.href = url
  a.download = `${prefix}_${t.getFullYear()}${pad(t.getMonth() + 1)}${pad(t.getDate())}_${pad(t.getHours())}${pad(t.getMinutes())}${pad(t.getSeconds())}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

async function doExport() {
  saveBlob(await exportRosters(buildParams(false)), '名单导出')
}

// ---------- 新增人员（学生/老师录入名单，管理员创建账号） ----------
const userStore = useUserStore()
const canRosterCreate = computed(() => hasPermission('system:roster:create', userStore.permissions))
const canUserCreate = computed(() => hasPermission('system:user:create', userStore.permissions))
const canCreatePerson = computed(() => canRosterCreate.value || canUserCreate.value)

const personCreateVisible = ref(false)
const personCreateFormRef = ref()
const personCreateForm = reactive({
  personType: 'STUDENT',
  personNo: '',
  accountName: '',
  realName: '',
  phone: '',
  password: '',
  departmentId: null
})
const personCreateRules = {
  personType: [{ required: true, message: '请选择身份', trigger: 'change' }],
  personNo: [
    { required: true, message: '请输入学工号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{2,32}$/, message: '2-32 位字母或数字', trigger: 'blur' }
  ],
  accountName: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/, message: '字母开头，3-32 位字母数字下划线', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: PHONE_PATTERN, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { pattern: PASSWORD_PATTERN, message: '至少 8 位，需同时包含字母和数字', trigger: 'blur' }
  ],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }]
}

function openPersonCreate() {
  personCreateForm.personType = canRosterCreate.value ? 'STUDENT' : 'ADMIN'
  personCreateForm.personNo = ''
  personCreateForm.accountName = ''
  personCreateForm.realName = ''
  personCreateForm.phone = ''
  personCreateForm.password = ''
  personCreateForm.departmentId = null
  personCreateVisible.value = true
}

function onPersonTypeChange() {
  personCreateFormRef.value?.clearValidate()
}

async function submitPersonCreate() {
  try {
    await personCreateFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (personCreateForm.personType === 'ADMIN') {
      const { accountName, realName, password, departmentId } = personCreateForm
      await createUser({ accountName, realName, password, departmentId })
      ElMessage.success('已创建，首次登录将要求修改密码')
    } else {
      const { personNo, personType, realName, phone, departmentId } = personCreateForm
      await createRoster({ personNo, personType, realName, phone, departmentId })
      ElMessage.success('已录入')
    }
    personCreateVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

// ---------- 编辑/启停名单 ----------
const rosterEditVisible = ref(false)
const rosterEditFormRef = ref()
const rosterEditRow = ref(null)
const rosterEditForm = reactive({ realName: '', phone: '', departmentId: null })
const rosterEditRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: PHONE_PATTERN, message: '手机号格式不正确', trigger: 'blur' }
  ],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }]
}

function openRosterEdit(row) {
  rosterEditRow.value = row
  rosterEditForm.realName = row.realName
  rosterEditForm.phone = row.phone
  rosterEditForm.departmentId = row.departmentId
  rosterEditVisible.value = true
}

async function submitRosterEdit() {
  try {
    await rosterEditFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await updateRoster(rosterEditRow.value.rosterId, { ...rosterEditForm })
    ElMessage.success('已保存')
    rosterEditVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function toggleRoster(row) {
  const enabling = row.status === 'ROSTER_DISABLED'
  if (!enabling) {
    try {
      await ElMessageBox.confirm('停用后该人员将无法首登建档，确定停用吗？', '停用名单', { type: 'warning' })
    } catch {
      return
    }
  }
  await updateRosterEnabled(row.rosterId, enabling ? 1 : 0)
  ElMessage.success(enabling ? '已启用' : '已停用')
  load()
}

// ---------- 编辑用户 ----------
const userEditVisible = ref(false)
const userEditFormRef = ref()
const userEditRow = ref(null)
const userEditForm = reactive({ realName: '', phone: '', departmentId: null })
const userEditRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: PHONE_PATTERN, message: '手机号格式不正确', trigger: 'blur' }],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }]
}

function openUserEdit(row) {
  userEditRow.value = row
  userEditForm.realName = row.realName
  userEditForm.phone = row.phone || ''
  userEditForm.departmentId = row.departmentId
  userEditVisible.value = true
}

async function submitUserEdit() {
  try {
    await userEditFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await updateUser(userEditRow.value.userId, {
      realName: userEditForm.realName,
      phone: userEditForm.phone || null,
      departmentId: userEditForm.departmentId
    })
    ElMessage.success('已保存')
    userEditVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function toggleUser(row) {
  const enabling = row.status === 'USER_DISABLED'
  if (!enabling) {
    try {
      await ElMessageBox.confirm('停用后该用户的登录会话将立即失效，确定停用吗？', '停用用户', { type: 'warning' })
    } catch {
      return
    }
  }
  await updateUserEnabled(row.userId, enabling ? 1 : 0)
  ElMessage.success(enabling ? '已启用' : '已停用')
  load()
}

// ---------- 重置密码 ----------
const resetVisible = ref(false)
const resetFormRef = ref()
const resetRow = ref(null)
const resetForm = reactive({ newPassword: '' })
const resetRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: PASSWORD_PATTERN, message: '至少 8 位，需同时包含字母和数字', trigger: 'blur' }
  ]
}

function openReset(row) {
  resetRow.value = row
  resetForm.newPassword = ''
  resetVisible.value = true
}

async function submitReset() {
  try {
    await resetFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await resetUserPassword(resetRow.value.userId, resetForm.newPassword)
    ElMessage.success('已重置，该用户须重新登录')
    resetVisible.value = false
  } finally {
    submitting.value = false
  }
}

// ---------- 绑定/解绑管理员角色 ----------
async function toggleAdminRole(row) {
  const unbinding = roleCodes(row).includes('ADMIN')
  try {
    await ElMessageBox.confirm(
      `${unbinding ? '解绑' : '绑定'}后，该老师当前登录会话将立即退出，确定${unbinding ? '解绑' : '绑定'}吗？`,
      `${unbinding ? '解绑' : '绑定'}管理员角色`,
      { type: 'warning' }
    )
  } catch {
    return
  }
  await updateUserAdminRole(row.userId, !unbinding)
  ElMessage.success(unbinding ? '已解绑' : '已绑定')
  load()
}

// ---------- 导入 CSV ----------
const importVisible = ref(false)
const importResultVisible = ref(false)
const importResult = ref(null)
const importFile = ref(null)
const uploadRef = ref()

function openImport() {
  importFile.value = null
  importVisible.value = true
}

function onImportFileChange(file) {
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 2MB')
    uploadRef.value?.clearFiles()
    importFile.value = null
    return
  }
  importFile.value = file.raw
}

function onImportFileExceed(files) {
  uploadRef.value?.clearFiles()
  uploadRef.value?.handleStart(files[0])
}

function onImportFileRemove() {
  importFile.value = null
}

async function downloadTemplate() {
  saveBlob(await downloadRosterImportTemplate(), '名单导入模板')
}

async function submitImport() {
  if (!importFile.value) return
  submitting.value = true
  try {
    const { ok, data } = await withSafeAuth(() => importRosters(importFile.value))
    if (!ok) return
    importResult.value = data
    importVisible.value = false
    importResultVisible.value = true
    load()
  } finally {
    submitting.value = false
  }
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detailRow = ref(null)

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

onMounted(async () => {
  load()
  deptOptions.value = await getDepartmentOptions()
})
</script>

<style scoped>
.person_page__card + .person_page__card {
  margin-top: 14px;
}

.person_page__filter {
  margin-bottom: 4px;
}

.person_page__toolbar {
  display: flex;
  gap: 0;
}

.person_page__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.person_page__role_tag {
  margin-right: 4px;
}

.person_page__hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.person_page__ok {
  color: var(--el-color-success);
  font-weight: 600;
}

.person_page__fail {
  color: var(--el-color-danger);
  font-weight: 600;
}

.person_page__upload_tip {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
