<template>
  <div class="grant_manage">
    <!-- 工具条 -->
    <div class="card grant_manage__card">
      <div class="grant_manage__toolbar">
        <div>
          <h2 class="t-title grant_manage__title">
            助理管理
          </h2>
          <p class="t-sub">
            授权学生助理在指定场地与有效期内代办物料领还 · 即时生效，可随时撤销 · 有效期最长一学年
          </p>
        </div>
        <el-button
          v-permission="'system:grant:create'"
          type="primary"
          @click="onCreate"
        >
          添加助理
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        row-key="id"
      >
        <el-table-column
          label="助理"
          min-width="130"
        >
          <template #default="{ row }">
            <div class="grant_manage__student">
              <span class="grant_manage__name">{{ row.studentName }}</span>
              <span class="t-hint">{{ row.studentNo }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="授权范围"
          min-width="220"
        >
          <template #default="{ row }">
            <div class="grant_manage__scopes">
              <span
                v-for="scope in row.scopes"
                :key="scope.id"
                class="grant_manage__scope"
              >
                {{ scope.labName || scope.labId }}
                <em
                  class="grant_manage__action"
                  :class="scope.actionCode === 'ISSUE' ? 'is_issue' : 'is_return'"
                >{{ ACTION_MAP[scope.actionCode] || scope.actionCode }}</em>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="有效期"
          min-width="180"
        >
          <template #default="{ row }">
            {{ formatInstant(row.startAt) }} ~ {{ formatInstant(row.endAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="90"
        >
          <template #default="{ row }">
            <span
              class="pill"
              :class="GRANT_STATUS_MAP[row.status]?.pill"
            >
              {{ GRANT_STATUS_MAP[row.status]?.label || row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="授权说明"
          min-width="140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.reason || '—' }}
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
              v-permission="'system:grant:revoke'"
              link
              type="danger"
              @click="onRevoke(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="t-hint">暂无授权记录，点击右上角「添加助理」发起授权</span>
        </template>
      </el-table>

      <div class="grant_manage__pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 新建授权弹窗 -->
    <el-dialog
      v-model="formVisible"
      title="添加助理"
      width="560px"
      :close-on-click-modal="false"
      @closed="onFormClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="92px"
      >
        <el-form-item
          label="学生"
          prop="studentId"
        >
          <el-autocomplete
            v-model="form.studentKeyword"
            :fetch-suggestions="fetchStudents"
            :debounce="200"
            :trigger-on-focus="false"
            placeholder="输入学号或姓名搜索"
            clearable
            class="grant_manage__full"
            value-key="label"
            @select="onSelectStudent"
            @clear="form.studentId = null"
          />
          <span class="t-hint grant_manage__hint">仅可搜索已注册且启用中的学生账号</span>
        </el-form-item>
        <el-form-item
          label="有效期"
          prop="dateRange"
        >
          <el-date-picker
            v-model="form.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="生效日期"
            end-placeholder="失效日期"
            value-format="YYYY-MM-DD"
            class="grant_manage__full"
          />
          <span class="t-hint grant_manage__hint">日期含起止当日；最长一学年</span>
        </el-form-item>
        <el-form-item
          label="场地范围"
          prop="labIds"
        >
          <el-select
            v-model="form.labIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择可代办的场地（可多选）"
            class="grant_manage__full"
          >
            <el-option
              v-for="lab in labOptions"
              :key="lab.id"
              :label="lab.labName"
              :value="lab.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="代办动作"
          prop="actions"
        >
          <el-checkbox-group v-model="form.actions">
            <el-checkbox value="ISSUE">
              代办领用
            </el-checkbox>
            <el-checkbox value="RETURN">
              代办归还
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="授权说明">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="选填，如 课代表代办本周实训物料领还"
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
          @click="onSubmit"
        >
          确认授权
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatInstant, shanghaiLocalToIso, addDaysShanghai, todayShanghai } from '@/utils/datetime'
import { pageMyGrants, searchGrantStudents, createGrant, revokeGrant } from '@/api/grant'
import { listSpaceOptions } from '@/api/space'

/** 授权状态字典（ACTIVE 生效中 / REVOKED 已撤销 / EXPIRED 已到期，后端派生） */
const GRANT_STATUS_MAP = {
  ACTIVE: { label: '生效中', pill: 'pill--ok' },
  REVOKED: { label: '已撤销', pill: 'pill--muted' },
  EXPIRED: { label: '已到期', pill: 'pill--warn' }
}

/** 代办动作字典 */
const ACTION_MAP = { ISSUE: '领用', RETURN: '归还' }

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })

const formVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const labOptions = ref([])

const emptyForm = () => ({
  studentId: null,
  studentKeyword: '',
  dateRange: [todayShanghai(), addDaysShanghai(todayShanghai(), 180)],
  labIds: [],
  actions: ['ISSUE', 'RETURN'],
  reason: ''
})

const form = reactive(emptyForm())

const rules = {
  studentId: [{ required: true, message: '请选择学生', trigger: 'change' }],
  dateRange: [{ required: true, message: '请选择有效期', trigger: 'change' }],
  labIds: [{ required: true, type: 'array', min: 1, message: '请选择至少一个场地', trigger: 'change' }],
  actions: [{ required: true, type: 'array', min: 1, message: '请选择至少一个代办动作', trigger: 'change' }]
}

function elapsedMs(begin) {
  return Math.round(performance.now() - begin)
}

async function loadData() {
  const begin = performance.now()
  loading.value = true
  try {
    const data = await pageMyGrants({ pageNum: query.pageNum, pageSize: query.pageSize })
    records.value = data.records || []
    total.value = Number(data.total) || 0
    console.info('[助理管理] 列表加载完成', {
      pageNum: query.pageNum, pageRows: records.value.length, total: total.value, costMs: elapsedMs(begin)
    })
  } catch (e) {
    console.error('[助理管理] 列表加载失败', { error: e?.message, costMs: elapsedMs(begin) })
    throw e
  } finally {
    loading.value = false
  }
}

/** 学生搜索联想：候选 label 供回填，raw 为学生原始数据 */
async function fetchStudents(keyword, cb) {
  const kw = String(keyword || '').trim()
  if (!kw) {
    cb([])
    return
  }
  try {
    const list = await searchGrantStudents({ keyword: kw })
    console.info('[助理管理] 学生搜索完成', { keyword: kw, count: list?.length || 0 })
    cb((list || []).map((s) => ({ ...s, label: `${s.realName}（${s.personNo}）` })))
  } catch (e) {
    console.warn('[助理管理] 学生搜索失败', { keyword: kw, error: e?.message })
    cb([])
  }
}

function onSelectStudent(item) {
  form.studentId = item.id
  form.studentKeyword = item.label
  console.info('[助理管理] 选定学生', { studentId: item.id, realName: item.realName, personNo: item.personNo })
}

async function onCreate() {
  if (!labOptions.value.length) {
    try {
      labOptions.value = await listSpaceOptions({ availableOnly: true, limit: 100 })
      console.info('[助理管理] 场地选项加载完成', { count: labOptions.value.length })
    } catch (e) {
      console.warn('[助理管理] 场地选项加载失败', { error: e?.message })
    }
  }
  formVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const [startDate, endDate] = form.dateRange || []
  // 半开区间 [startAt, endAt)：失效日期当日 24 点 = 次日 00:00（上海）
  const payload = {
    studentId: Number(form.studentId),
    startAt: shanghaiLocalToIso(startDate, '00:00'),
    endAt: shanghaiLocalToIso(addDaysShanghai(endDate, 1), '00:00'),
    reason: form.reason?.trim() || null,
    scopes: form.labIds.flatMap((labId) =>
      form.actions.map((actionCode) => ({ scopeType: 'LAB', labId: Number(labId), actionCode })))
  }
  submitting.value = true
  try {
    console.info('[助理管理] 提交授权', { studentId: payload.studentId, scopeCount: payload.scopes.length, startAt: payload.startAt, endAt: payload.endAt })
    await createGrant(payload)
    ElMessage.success('授权已生效')
    formVisible.value = false
    query.pageNum = 1
    loadData()
  } catch (e) {
    console.warn('[助理管理] 授权提交失败', { error: e?.message })
    // 错误提示由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function onRevoke(row) {
  try {
    await ElMessageBox.confirm(
      `确定撤销对「${row.studentName}」的助理授权吗？撤销后该学生立即失去对应场地的代办权限。`,
      '撤销确认',
      { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  await revokeGrant(row.id)
  ElMessage.success('已撤销')
  console.info('[助理管理] 撤销授权完成', { grantId: row.id, studentName: row.studentName })
  loadData()
}

function onFormClosed() {
  Object.assign(form, emptyForm())
  formRef.value?.resetFields()
}

onMounted(loadData)
</script>

<style scoped>
.grant_manage {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.grant_manage__card {
  padding: 20px;
}

.grant_manage__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.grant_manage__title {
  margin: 0 0 4px;
  font-size: 18px;
}

.grant_manage__student {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.grant_manage__name {
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.grant_manage__scopes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.grant_manage__scope {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--g2);
}

.grant_manage__action {
  font-style: normal;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: var(--r-full, 999px);
}

.grant_manage__action.is_issue {
  color: var(--brand);
  background: var(--info-bg);
}

.grant_manage__action.is_return {
  color: var(--ok);
  background: var(--ok-bg, rgba(46, 158, 91, .1));
}

.grant_manage__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.grant_manage__full {
  width: 100%;
}

.grant_manage__hint {
  display: block;
  margin-top: 4px;
  line-height: 1.5;
}
</style>
