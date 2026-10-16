<template>
  <div class="restriction_page">
    <!-- 筛选栏 -->
    <el-card class="restriction_page__card">
      <div class="restriction_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="姓名 / 账号 / 学工号"
              clearable
              style="width: 200px"
              @keyup.enter="search"
              @clear="search"
            />
          </el-form-item>
          <el-form-item label="限制类型">
            <el-select
              v-model="query.restrictionType"
              placeholder="全部"
              clearable
              style="width: 130px"
              @change="search"
            >
              <el-option
                label="器材"
                value="EQUIPMENT"
              />
              <el-option
                label="场地"
                value="LAB"
              />
              <el-option
                label="全部预约"
                value="ALL"
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
                label="生效中"
                value="ACTIVE"
              />
              <el-option
                label="未开始"
                value="PENDING"
              />
              <el-option
                label="已结束"
                value="ENDED"
              />
              <el-option
                label="已撤销"
                value="REVOKED"
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
        <div v-permission="'system:restriction:create'">
          <el-button
            type="primary"
            @click="openCreate"
          >
            新增限制
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 限制表格 -->
    <el-card class="restriction_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          label="被限制用户"
          min-width="170"
        >
          <template #default="{ row }">
            {{ row.userName }}
            <span class="restriction_page__sub">{{ row.personNo || row.accountName }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="限制类型"
          width="110"
        >
          <template #default="{ row }">
            <el-tag
              :type="typeTag(row.restrictionType)"
              size="small"
            >
              {{ typeLabel(row.restrictionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="限制区间"
          min-width="235"
        >
          <template #default="{ row }">
            {{ formatInstant(row.startAt) }} ~ {{ row.endAt ? formatInstant(row.endAt) : '直至撤销' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="reason"
          label="原因"
          min-width="160"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.reason || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusOf(row).type"
              size="small"
            >
              {{ statusOf(row).label }}
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
          width="90"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="!row.revokedAt"
              v-permission="'system:restriction:revoke'"
              type="danger"
              link
              size="small"
              @click="revokeRow(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无预约限制" />
        </template>
      </el-table>
      <div class="restriction_page__pager">
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
      <p class="restriction_page__note t-hint">
        生效中的限制将在预约下单时拦截（随预约模块落地接入）；超期未归还的自动冻结由单据模块补充。
      </p>
    </el-card>

    <!-- 新增限制 -->
    <el-dialog
      v-model="createVisible"
      title="新增预约限制"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="96px"
      >
        <el-form-item
          label="被限制用户"
          prop="userId"
        >
          <el-select
            v-model="createForm.userId"
            placeholder="输入姓名 / 账号 / 学工号搜索"
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userSearching"
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.userId"
              :label="`${u.realName}（${u.personNo || u.accountName}）`"
              :value="u.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="限制类型"
          prop="restrictionType"
        >
          <el-radio-group v-model="createForm.restrictionType">
            <el-radio value="EQUIPMENT">
              器材
            </el-radio>
            <el-radio value="LAB">
              场地
            </el-radio>
            <el-radio value="ALL">
              全部预约
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="开始时间"
          prop="startAt"
        >
          <el-date-picker
            v-model="createForm.startAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="上海本地时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="createForm.endAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空为直至撤销"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="原因">
          <el-input
            v-model="createForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="选填，如：逾期未归还器材"
          />
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
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createRestriction,
  getRestrictionUserOptions,
  pageRestrictions,
  revokeRestriction
} from '@/api/system/restriction'
import { formatInstant, toUtcIso } from '@/utils/datetime'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', restrictionType: '', status: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.keyword) params.keyword = undefined
    if (!params.restrictionType) params.restrictionType = undefined
    if (!params.status) params.status = undefined
    const data = await pageRestrictions(params)
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
  query.restrictionType = ''
  query.status = ''
  search()
}

function typeLabel(t) {
  return { EQUIPMENT: '器材', LAB: '场地', ALL: '全部预约' }[t] || t
}

function typeTag(t) {
  return { EQUIPMENT: 'primary', LAB: 'success', ALL: 'warning' }[t] || 'info'
}

function statusOf(row) {
  if (row.revokedAt) return { label: '已撤销', type: 'info' }
  const now = Date.now()
  const start = Date.parse(row.startAt)
  const end = row.endAt ? Date.parse(row.endAt) : null
  if (start > now) return { label: '未开始', type: 'info' }
  if (end !== null && end <= now) return { label: '已结束', type: 'info' }
  return { label: '生效中', type: 'danger' }
}

// ---------- 新增 ----------
const createVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ userId: '', restrictionType: 'EQUIPMENT', startAt: '', endAt: '', reason: '' })
const userOptions = ref([])
const userSearching = ref(false)

const createRules = {
  userId: [{ required: true, message: '请选择被限制用户', trigger: 'change' }],
  restrictionType: [{ required: true, message: '请选择限制类型', trigger: 'change' }],
  startAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }]
}

async function searchUsers(keyword) {
  userSearching.value = true
  try {
    userOptions.value = await getRestrictionUserOptions(keyword || undefined)
  } finally {
    userSearching.value = false
  }
}

async function openCreate() {
  createForm.userId = ''
  createForm.restrictionType = 'EQUIPMENT'
  createForm.startAt = ''
  createForm.endAt = ''
  createForm.reason = ''
  createVisible.value = true
  await searchUsers('')
}

async function submitCreate() {
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  let payload
  try {
    payload = {
      userId: Number(createForm.userId),
      restrictionType: createForm.restrictionType,
      reason: createForm.reason || null,
      startAt: toUtcIso(createForm.startAt),
      endAt: createForm.endAt ? toUtcIso(createForm.endAt) : null
    }
  } catch (e) {
    ElMessage.error(e.message)
    return
  }
  submitting.value = true
  try {
    await createRestriction(payload)
    ElMessage.success('已创建')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function revokeRow(row) {
  try {
    await ElMessageBox.confirm(
      `撤销后「${row.userName}」的该条限制立即失效，确定撤销吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await revokeRestriction(row.id)
  ElMessage.success('已撤销')
  load()
}

onMounted(load)
</script>

<style scoped>
.restriction_page__card + .restriction_page__card {
  margin-top: 14px;
}

.restriction_page__filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.restriction_page__filter :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}

.restriction_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.restriction_page__sub {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.restriction_page__note {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.7;
}
</style>
