<template>
  <div class="user_page">
    <!-- 筛选栏 -->
    <el-card class="user_page__card">
      <div class="user_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="姓名 / 学工号 / 手机号 / 账号"
              clearable
              style="width: 230px"
              @keyup.enter="search"
              @clear="search"
            />
          </el-form-item>
          <el-form-item label="类型">
            <el-select
              v-model="query.userType"
              placeholder="全部"
              clearable
              style="width: 120px"
              @change="search"
            >
              <el-option
                v-for="t in userTypes"
                :key="t.value"
                :label="t.label"
                :value="t.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="query.enabled"
              placeholder="全部"
              clearable
              style="width: 110px"
              @change="search"
            >
              <el-option
                label="正常"
                :value="1"
              />
              <el-option
                label="停用"
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
        <el-button
          v-permission="'system:user:create'"
          type="primary"
          @click="openCreate"
        >
          新建管理员
        </el-button>
      </div>
    </el-card>

    <!-- 用户表格 -->
    <el-card class="user_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          prop="realName"
          label="姓名"
          min-width="110"
        />
        <el-table-column
          prop="personNo"
          label="学工号"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.personNo || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="类型"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              :type="typeTag(row.userType)"
              size="small"
            >
              {{ typeLabel(row.userType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="phone"
          label="手机号"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.phone || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="departmentName"
          label="部门"
          min-width="130"
        />
        <el-table-column
          label="状态"
          width="140"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.enabled ? 'success' : 'info'"
              size="small"
            >
              {{ row.enabled ? '正常' : '停用' }}
            </el-tag>
            <el-tag
              v-if="row.locked"
              type="warning"
              size="small"
              class="user_page__lock_tag"
            >
              锁定中
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="创建时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="200"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'system:user:update'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
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
              v-permission="'system:user:enable'"
              link
              :type="row.enabled ? 'danger' : 'success'"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户" />
        </template>
      </el-table>
      <div class="user_page__pager">
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

    <!-- 新建管理员 -->
    <el-dialog
      v-model="createVisible"
      title="新建管理员"
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
          label="登录账号"
          prop="accountName"
        >
          <el-input
            v-model="createForm.accountName"
            placeholder="字母开头，3-32 位字母数字下划线"
          />
        </el-form-item>
        <el-form-item
          label="姓名"
          prop="realName"
        >
          <el-input
            v-model="createForm.realName"
            placeholder="真实姓名"
          />
        </el-form-item>
        <el-form-item
          label="初始密码"
          prop="password"
        >
          <el-input
            v-model="createForm.password"
            type="password"
            show-password
            placeholder="8-64 位，含字母和数字"
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
      <p class="user_page__tip t-hint">
        学生 / 老师账号请通过「名单管理」预录，由本人短信首登建档；新管理员首次登录将被要求修改密码。
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
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户 -->
    <el-dialog
      v-model="editVisible"
      title="编辑用户"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="86px"
      >
        <el-form-item label="账号">
          <el-input
            :model-value="editRow?.accountName || editRow?.personNo || '—'"
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
            placeholder="11 位手机号，可留空"
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

    <!-- 重置密码 -->
    <el-dialog
      v-model="resetVisible"
      title="重置密码"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="resetFormRef"
        :model="resetForm"
        :rules="resetRules"
        label-width="86px"
      >
        <el-form-item label="账号">
          <el-input
            :model-value="resetRow?.accountName || resetRow?.personNo || '—'"
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
            placeholder="8-64 位，含字母和数字"
          />
        </el-form-item>
      </el-form>
      <p class="user_page__tip t-hint">
        重置后「{{ resetRow?.realName }}」的全部登录会话将立即退出，下次登录须先修改密码。该操作需二次密码确认。
      </p>
      <template #footer>
        <el-button @click="resetVisible = false">
          取消
        </el-button>
        <el-button
          type="warning"
          :loading="submitting"
          @click="submitReset"
        >
          重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createUser,
  getDepartmentOptions,
  pageUsers,
  resetUserPassword,
  updateUser,
  updateUserEnabled
} from '@/api/system/user'
import { formatInstant } from '@/utils/datetime'

const userTypes = [
  { label: '学生', value: 'STUDENT' },
  { label: '老师', value: 'TEACHER' },
  { label: '管理员', value: 'ADMIN' }
]

const typeLabel = (t) => userTypes.find((x) => x.value === t)?.label || t
const typeTag = (t) => ({ ADMIN: 'warning', TEACHER: 'primary', STUDENT: 'success' })[t] || 'info'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', userType: '', enabled: null })
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
    if (!params.userType) params.userType = undefined
    if (params.enabled === null || params.enabled === '') params.enabled = undefined
    const data = await pageUsers(params)
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
  query.userType = ''
  query.enabled = null
  search()
}

// ---------- 新建管理员 ----------
const createVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ accountName: '', realName: '', password: '', departmentId: null })

const passwordRule = {
  pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/,
  message: '密码需 8-64 位且同时包含字母和数字',
  trigger: 'blur'
}

const createRules = {
  accountName: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/, message: '字母开头，3-32 位字母、数字或下划线', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }, passwordRule],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }]
}

function openCreate() {
  createForm.accountName = ''
  createForm.realName = ''
  createForm.password = ''
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
    await createUser({ ...createForm })
    ElMessage.success('已创建，首次登录将要求修改密码')
    createVisible.value = false
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
  phone: [{ pattern: /^1\d{10}$/, message: '手机号格式不正确', trigger: 'blur' }],
  departmentId: [{ required: true, message: '请选择部门', trigger: 'change' }]
}

function openEdit(row) {
  editRow.value = row
  editForm.realName = row.realName
  editForm.phone = row.phone || ''
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
    await updateUser(editRow.value.id, {
      realName: editForm.realName,
      phone: editForm.phone || null,
      departmentId: editForm.departmentId
    })
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
        ? `停用后「${row.realName}」将立即退出登录且无法再登录，确定停用吗？`
        : `启用「${row.realName}」并解除其登录锁定，确定启用吗？`,
      '提示',
      { type: 'warning', confirmButtonText: disabling ? '停用' : '启用' }
    )
  } catch {
    return
  }
  await updateUserEnabled(row.id, disabling ? 0 : 1)
  ElMessage.success(disabling ? '已停用' : '已启用')
  load()
}

// ---------- 重置密码 ----------
const resetVisible = ref(false)
const resetFormRef = ref()
const resetRow = ref(null)
const resetForm = reactive({ newPassword: '' })

const resetRules = {
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, passwordRule]
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
    await resetUserPassword(resetRow.value.id, resetForm.newPassword)
    ElMessage.success('已重置，该用户需重新登录并修改密码')
    resetVisible.value = false
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
.user_page__card + .user_page__card {
  margin-top: 14px;
}

.user_page__filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.user_page__filter :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}

.user_page__lock_tag {
  margin-left: 6px;
}

.user_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.user_page__tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
}
</style>
