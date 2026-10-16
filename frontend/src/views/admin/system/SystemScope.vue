<template>
  <div class="scope_page">
    <!-- 筛选栏 -->
    <el-card class="scope_page__card">
      <div class="scope_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="管理员姓名 / 账号"
              clearable
              style="width: 200px"
              @keyup.enter="search"
              @clear="search"
            />
          </el-form-item>
          <el-form-item label="范围类型">
            <el-select
              v-model="query.scopeType"
              placeholder="全部"
              clearable
              style="width: 130px"
              @change="search"
            >
              <el-option
                label="全校"
                value="ALL"
              />
              <el-option
                label="部门"
                value="DEPARTMENT"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="操作级别">
            <el-select
              v-model="query.accessMode"
              placeholder="全部"
              clearable
              style="width: 120px"
              @change="search"
            >
              <el-option
                label="可读"
                value="READ"
              />
              <el-option
                label="可写"
                value="WRITE"
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
        <div v-permission="'system:scope:grant'">
          <el-button
            type="primary"
            @click="openCreate"
          >
            新增授权
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 范围表格 -->
    <el-card class="scope_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          label="管理员"
          min-width="170"
        >
          <template #default="{ row }">
            {{ row.userName }}
            <span class="scope_page__account">{{ row.accountName }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="范围类型"
          width="110"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.scopeType === 'ALL' ? 'success' : 'primary'"
              size="small"
            >
              {{ row.scopeType === 'ALL' ? '全校' : '部门' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="目标"
          min-width="180"
        >
          <template #default="{ row }">
            {{ row.scopeType === 'ALL' ? '全校（不填目标）' : row.departmentName || `#${row.departmentId}` }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作级别"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.accessMode === 'WRITE' ? 'warning' : 'info'"
              size="small"
            >
              {{ row.accessMode === 'WRITE' ? '可写' : '可读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="授权时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="90"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'system:scope:grant'"
              type="danger"
              link
              size="small"
              @click="removeRow(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无范围授权" />
        </template>
      </el-table>
      <div class="scope_page__pager">
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
      <p class="scope_page__note t-hint">
        无范围记录不自动授予全校数据；可写天然包含可读；同目标升降级通过增删行实现。仅全校写范围管理员可维护本页授权。
      </p>
    </el-card>

    <!-- 新增授权 -->
    <el-dialog
      v-model="createVisible"
      title="新增范围授权"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="96px"
      >
        <el-form-item
          label="管理员"
          prop="userId"
        >
          <el-select
            v-model="createForm.userId"
            placeholder="选择管理员"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="a in adminOptions"
              :key="a.userId"
              :label="`${a.realName}（${a.accountName}）`"
              :value="a.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="范围类型"
          prop="scopeType"
        >
          <el-select
            v-model="createForm.scopeType"
            style="width: 100%"
            @change="createForm.departmentId = ''"
          >
            <el-option
              label="全校"
              value="ALL"
            />
            <el-option
              label="部门"
              value="DEPARTMENT"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="createForm.scopeType === 'DEPARTMENT'"
          label="目标部门"
          prop="departmentId"
        >
          <el-select
            v-model="createForm.departmentId"
            placeholder="选择部门"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="d in departmentOptions"
              :key="d.id"
              :label="d.departmentName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="操作级别"
          prop="accessMode"
        >
          <el-radio-group v-model="createForm.accessMode">
            <el-radio value="READ">
              可读
            </el-radio>
            <el-radio value="WRITE">
              可写（含可读）
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCreate"
        >
          授权
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createScope, deleteScope, getAdminOptions, pageScopes } from '@/api/system/scope'
import { withSafeAuth } from '@/utils/safe_auth'
import { getDepartmentOptions } from '@/api/system/user'
import { formatInstant } from '@/utils/datetime'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', scopeType: '', accessMode: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.keyword) params.keyword = undefined
    if (!params.scopeType) params.scopeType = undefined
    if (!params.accessMode) params.accessMode = undefined
    const data = await pageScopes(params)
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
  query.scopeType = ''
  query.accessMode = ''
  search()
}

// ---------- 新增授权 ----------
const createVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ userId: '', scopeType: 'DEPARTMENT', departmentId: '', accessMode: 'READ' })
const adminOptions = ref([])
const departmentOptions = ref([])

const createRules = {
  userId: [{ required: true, message: '请选择管理员', trigger: 'change' }],
  scopeType: [{ required: true, message: '请选择范围类型', trigger: 'change' }],
  departmentId: [{ required: true, message: '请选择目标部门', trigger: 'change' }],
  accessMode: [{ required: true, message: '请选择操作级别', trigger: 'change' }]
}

async function openCreate() {
  createForm.userId = ''
  createForm.scopeType = 'DEPARTMENT'
  createForm.departmentId = ''
  createForm.accessMode = 'READ'
  createVisible.value = true
  adminOptions.value = await getAdminOptions()
  departmentOptions.value = await getDepartmentOptions()
}

async function submitCreate() {
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const res = await withSafeAuth(() => createScope({
      userId: Number(createForm.userId),
      scopeType: createForm.scopeType,
      departmentId: createForm.scopeType === 'DEPARTMENT' ? Number(createForm.departmentId) : null,
      accessMode: createForm.accessMode
    }))
    if (!res.ok) return
    ElMessage.success('已授权')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function removeRow(row) {
  try {
    await ElMessageBox.confirm(
      `删除后「${row.userName}」将失去该范围数据权限，确定删除吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  const res = await withSafeAuth(() => deleteScope(row.id))
  if (!res.ok) return
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.scope_page__card + .scope_page__card {
  margin-top: 14px;
}

.scope_page__filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.scope_page__filter :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}

.scope_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.scope_page__account {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.scope_page__note {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.7;
}
</style>
