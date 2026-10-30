<template>
  <section class="card assistant_grant">
    <div class="assistant_grant__head">
      <div>
        <h3 class="assistant_grant__title">
          助理授权
        </h3>
        <p class="t-sub">
          授权学生在指定时间内代领本人申领物料；撤销后未实发的代领将不可出库。
        </p>
      </div>
      <div class="assistant_grant__actions">
        <el-button
          :loading="loading"
          @click="load"
        >
          刷新
        </el-button>
        <el-button
          type="primary"
          @click="openCreate"
        >
          新建授权
        </el-button>
      </div>
    </div>

    <el-form
      :inline="true"
      class="assistant_grant__filters"
      @submit.prevent
    >
      <el-form-item label="授权状态">
        <el-select
          v-model="query.status"
          clearable
          placeholder="全部状态"
          class="assistant_grant__status_filter"
          @change="search"
        >
          <el-option
            label="生效中"
            value="ACTIVE"
          />
          <el-option
            label="已失效"
            value="EXPIRED"
          />
          <el-option
            label="已撤销"
            value="REVOKED"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="records"
      class="table-scroll-x"
      row-key="id"
    >
      <el-table-column
        label="代领学生"
        min-width="190"
      >
        <template #default="{ row }">
          <div class="t-strong">
            {{ row.studentName || row.studentId }}
          </div>
          <div class="t-hint">
            {{ [row.studentAccount, row.studentNo].filter(Boolean).join(' · ') || `ID：${row.studentId}` }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="生效 / 失效时间"
        min-width="190"
      >
        <template #default="{ row }">
          <div>{{ formatInstant(row.startAt) || '—' }}</div>
          <div class="t-hint">
            至 {{ formatInstant(row.endAt) || '—' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="105"
      >
        <template #default="{ row }">
          <span
            class="pill"
            :class="row.status === 'ACTIVE' ? 'pill--ok' : 'pill--muted'"
          >
            {{ assistantStatusLabel(row.status) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        label="授权原因"
        min-width="170"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.reason || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="创建 / 撤销信息"
        min-width="200"
      >
        <template #default="{ row }">
          <div>{{ formatInstant(row.createTime) || '—' }}</div>
          <div
            v-if="row.revokedAt"
            class="t-hint"
          >
            {{ formatInstant(row.revokedAt) }} · {{ row.revokedBy || '已撤销' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="90"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'ACTIVE'"
            link
            type="danger"
            :loading="revokingId === String(row.id)"
            @click="confirmRevoke(row)"
          >
            撤销
          </el-button>
          <span
            v-else
            class="t-hint"
          >—</span>
        </template>
      </el-table-column>
      <template #empty>
        <span class="t-hint">{{ query.status ? '当前状态下暂无授权记录' : '暂无助理授权，可点击“新建授权”添加' }}</span>
      </template>
    </el-table>

    <div class="assistant_grant__pager">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="changePageSize"
        @current-change="load"
      />
    </div>

    <el-dialog
      v-model="formVisible"
      title="新建助理授权"
      width="min(560px, 94vw)"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item
          label="代领学生"
          prop="studentId"
          required
        >
          <el-select
            v-model="form.studentId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchStudents"
            :loading="studentsLoading"
            placeholder="输入姓名、账号或学号搜索"
            class="assistant_grant__control"
          >
            <el-option
              v-for="student in studentOptions"
              :key="student.id"
              :label="`${student.realName || student.id}${student.personNo ? ` · ${student.personNo}` : ''}`"
              :value="String(student.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="授权时间"
          prop="timeRange"
          required
        >
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="生效时间"
            end-placeholder="失效时间"
            class="assistant_grant__control"
          />
        </el-form-item>
        <el-form-item label="授权原因">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
            placeholder="说明授权用途（选填）"
          />
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
          确认授权
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAssistant,
  pageAssistants,
  revokeAssistant,
  searchAssistantStudents
} from '@/api/item/assistant'
import { formatInstant } from '@/utils/datetime'
import { assistantStatusLabel, buildAssistantGrantPayload } from './assistant.rules'

const records = ref([])
const total = ref(0)
const loading = ref(false)
const revokingId = ref('')
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })

const formVisible = ref(false)
const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ studentId: '', timeRange: [], reason: '' })
const formRules = {
  studentId: [{ required: true, message: '请选择代领学生', trigger: 'change' }],
  timeRange: [{
    required: true,
    validator: (_rule, value, callback) => callback(
      Array.isArray(value) && value.length === 2 ? undefined : new Error('请选择授权生效与失效时间')
    ),
    trigger: 'change'
  }]
}
const studentOptions = ref([])
const studentsLoading = ref(false)
let studentSearchSerial = 0

async function load() {
  loading.value = true
  try {
    const result = await pageAssistants({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      sortBy: 'createTime',
      sortOrder: 'desc'
    })
    records.value = (result?.records || []).map((item) => ({ ...item, id: String(item.id) }))
    total.value = Number(result?.total) || 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function changePageSize() {
  query.pageNum = 1
  load()
}

async function searchStudents(keyword = '') {
  const serial = ++studentSearchSerial
  studentsLoading.value = true
  try {
    const result = await searchAssistantStudents(keyword.trim())
    if (serial === studentSearchSerial) studentOptions.value = result || []
  } catch {
    if (serial === studentSearchSerial) studentOptions.value = []
  } finally {
    if (serial === studentSearchSerial) studentsLoading.value = false
  }
}

function openCreate() {
  formVisible.value = true
  searchStudents()
}

function resetForm() {
  Object.assign(form, { studentId: '', timeRange: [], reason: '' })
  studentOptions.value = []
  formRef.value?.clearValidate()
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  let payload
  try {
    payload = buildAssistantGrantPayload(form)
  } catch (error) {
    ElMessage.warning(error.message)
    return
  }
  submitting.value = true
  try {
    await createAssistant(payload)
    ElMessage.success('助理授权已创建')
    formVisible.value = false
    query.pageNum = 1
    await load()
  } finally {
    submitting.value = false
  }
}

async function confirmRevoke(row) {
  try {
    await ElMessageBox.confirm(
      `确定撤销 ${row.studentName || '该学生'} 的代领授权吗？撤销后未实发的代领将不可出库。`,
      '撤销助理授权',
      { type: 'warning', confirmButtonText: '确定撤销', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  revokingId.value = String(row.id)
  try {
    await revokeAssistant(row.id)
    ElMessage.success('助理授权已撤销')
    await load()
  } finally {
    revokingId.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.assistant_grant {
  padding: 18px 20px;
}

.assistant_grant__head,
.assistant_grant__actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.assistant_grant__title {
  margin: 0 0 5px;
  font-size: 16px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.assistant_grant__head p {
  margin: 0;
}

.assistant_grant__filters {
  margin-top: 14px;
}

.assistant_grant__status_filter {
  width: 150px;
}

.assistant_grant__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.assistant_grant__control {
  width: 100%;
}

@media (max-width: 768px) {
  .assistant_grant {
    padding: 14px;
  }

  .assistant_grant__head {
    flex-direction: column;
  }

  .assistant_grant__actions {
    width: 100%;
  }

  .assistant_grant__actions .el-button {
    flex: 1;
    margin-left: 0;
  }
}
</style>
