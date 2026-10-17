<template>
  <div class="grant_page">
    <!-- 筛选栏 -->
    <el-card class="grant_page__card">
      <div class="grant_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="学生">
            <el-input
              v-model="query.keyword"
              placeholder="姓名 / 学号"
              clearable
              style="width: 180px"
              @keyup.enter="search"
              @clear="search"
            />
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
                label="生效中"
                value="ACTIVE"
              />
              <el-option
                label="已撤销"
                value="REVOKED"
              />
              <el-option
                label="已过期"
                value="EXPIRED"
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
          v-permission="'system:grant:create'"
          type="primary"
          @click="openCreate"
        >
          新建授权
        </el-button>
      </div>
    </el-card>

    <!-- 授权表格 -->
    <el-card class="grant_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          label="被授权学生"
          min-width="150"
        >
          <template #default="{ row }">
            {{ row.studentName }}
            <span class="t-hint">{{ row.studentNo }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="授权期限"
          min-width="300"
        >
          <template #default="{ row }">
            {{ formatInstant(row.startAt) }} ~ {{ formatInstant(row.endAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="剩余有效期"
          width="110"
        >
          <template #default="{ row }">
            <span
              v-if="row.status === 'ACTIVE'"
              :class="{ 'grant_page__soon': isExpiringSoon(row) }"
            >
              {{ remainLabel(row) }}
            </span>
            <span
              v-else
              class="t-hint"
            >—</span>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusTag(row.status)"
              size="small"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="reason"
          label="授权说明"
          min-width="160"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.reason || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="150"
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
              v-permission="'system:grant:revoke'"
              link
              type="danger"
              :disabled="row.status !== 'ACTIVE'"
              @click="revoke(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无授权记录" />
        </template>
      </el-table>
      <div class="grant_page__pager">
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

    <!-- 新建授权 -->
    <el-dialog
      v-model="createVisible"
      title="新建助理授权"
      width="440px"
      destroy-on-close
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="86px"
      >
        <el-form-item
          label="学生"
          prop="studentId"
        >
          <el-select
            v-model="createForm.studentId"
            filterable
            remote
            :remote-method="searchStudents"
            :loading="studentLoading"
            placeholder="输入姓名或学号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="s in studentOptions"
              :key="s.id"
              :label="`${s.realName}（${s.personNo}）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="开始时间"
          prop="startAt"
        >
          <el-date-picker
            v-model="createForm.startAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="结束时间"
          prop="endAt"
        >
          <el-date-picker
            v-model="createForm.endAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="授权说明">
          <el-input
            v-model="createForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="选填，如：协助 XX 课程设备领还"
          />
        </el-form-item>
      </el-form>
      <p class="grant_page__tip t-hint">
        单笔授权最长一学年（366 天）；生效期间被授权学生自动获得助理身份，到期或撤销后自动失效。授权与撤销会使学生现有登录会话失效，需重新登录。
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
          授权
        </el-button>
      </template>
    </el-dialog>

    <!-- 授权详情 -->
    <el-drawer
      v-model="detailVisible"
      title="授权详情"
      size="420px"
    >
      <el-descriptions
        v-if="detailRow"
        :column="1"
        border
      >
        <el-descriptions-item label="被授权学生">
          {{ detailRow.studentName }}（{{ detailRow.studentNo }}）
        </el-descriptions-item>
        <el-descriptions-item label="授权老师">
          {{ detailRow.teacherName }}
        </el-descriptions-item>
        <el-descriptions-item label="授权期限">
          {{ formatInstant(detailRow.startAt) }} ~ {{ formatInstant(detailRow.endAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag
            :type="statusTag(detailRow.status)"
            size="small"
          >
            {{ statusLabel(detailRow.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item
          v-if="detailRow.status === 'ACTIVE'"
          label="剩余有效期"
        >
          <span :class="{ 'grant_page__soon': isExpiringSoon(detailRow) }">
            {{ remainLabel(detailRow) }}
          </span>
        </el-descriptions-item>
        <template v-if="detailRow.status === 'REVOKED'">
          <el-descriptions-item label="撤销人">
            {{ detailRow.revokedByName || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="撤销时间">
            {{ formatInstant(detailRow.revokedAt) }}
          </el-descriptions-item>
        </template>
        <el-descriptions-item label="授权说明">
          {{ detailRow.reason || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatInstant(detailRow.createTime) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createGrant, pageMyGrants, revokeGrant, searchStudentOptions } from '@/api/grant'
import { formatInstant, toUtcIso } from '@/utils/datetime'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', status: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

const statusLabel = (s) => ({ ACTIVE: '生效中', REVOKED: '已撤销', EXPIRED: '已过期' })[s] || s
const statusTag = (s) => ({ ACTIVE: 'success', REVOKED: 'danger', EXPIRED: 'info' })[s] || 'info'

/** 剩余有效期（endAt 为带 Z 的 UTC 字符串，可直接解析比较） */
function remainMs(row) {
  return new Date(row.endAt).getTime() - Date.now()
}

function remainLabel(row) {
  const ms = remainMs(row)
  if (ms <= 0) return '已到期'
  const days = Math.floor(ms / 86400000)
  if (days >= 1) return `剩余 ${days} 天`
  const hours = Math.floor(ms / 3600000)
  return hours >= 1 ? `剩余 ${hours} 小时` : '不足 1 小时'
}

/** 72 小时内到期视为即将到期，醒目标识 */
function isExpiringSoon(row) {
  const ms = remainMs(row)
  return ms > 0 && ms <= 3 * 86400000
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detailRow = ref(null)

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.keyword) params.keyword = undefined
    if (!params.status) params.status = undefined
    const data = await pageMyGrants(params)
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
  query.status = ''
  search()
}

// ---------- 新建授权 ----------
const createVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ studentId: null, startAt: '', endAt: '', reason: '' })
const studentOptions = ref([])
const studentLoading = ref(false)

const createRules = {
  studentId: [{ required: true, message: '请选择学生', trigger: 'change' }],
  startAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endAt: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

async function searchStudents(keyword) {
  studentLoading.value = true
  try {
    studentOptions.value = await searchStudentOptions(keyword || undefined)
  } finally {
    studentLoading.value = false
  }
}

function openCreate() {
  createForm.studentId = null
  createForm.startAt = ''
  createForm.endAt = ''
  createForm.reason = ''
  studentOptions.value = []
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
    await createGrant({
      studentId: createForm.studentId,
      startAt: toUtcIso(createForm.startAt),
      endAt: toUtcIso(createForm.endAt),
      reason: createForm.reason || null
    })
    ElMessage.success('已授权；若学生当前在线需重新登录，生效期内自动获得助理身份')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

// ---------- 撤销 ----------
async function revoke(row) {
  try {
    await ElMessageBox.confirm(
      `撤销后「${row.studentName}」将立即失去助理身份，确定撤销吗？`,
      '撤销授权',
      { type: 'warning', confirmButtonText: '撤销' }
    )
  } catch {
    return
  }
  await revokeGrant(row.id)
  ElMessage.success('已撤销')
  load()
}

onMounted(load)
</script>

<style scoped>
.grant_page__card + .grant_page__card {
  margin-top: 14px;
}

.grant_page__filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.grant_page__filter :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}

.grant_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.grant_page__tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
}

.grant_page__soon {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
