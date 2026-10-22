<template>
  <div class="policy_page page">
    <div class="policy_page__header">
      <div>
        <h3 class="policy_page__title">
          参数配置
        </h3>
        <p class="policy_page__subtitle">
          修改后保存即发布新版本并立即生效，旧版本自动停用；历史版本可回溯查看
        </p>
      </div>
      <el-button
        :icon="Clock"
        @click="openHistory"
      >
        历史版本
      </el-button>
    </div>

    <div
      v-loading="loading"
      class="policy_page__groups"
    >
      <section
        v-for="group in GROUPS"
        :key="group.key"
        class="policy_group"
      >
        <header class="policy_group__header">
          <span class="policy_group__icon">
            <el-icon :size="18">
              <component :is="group.icon" />
            </el-icon>
          </span>
          <div>
            <div class="policy_group__title">
              {{ group.title }}
            </div>
            <div class="policy_group__subtitle">
              {{ group.subtitle }}
            </div>
          </div>
        </header>

        <div class="policy_group__body">
          <div
            v-for="code in group.codes"
            :key="code"
            class="policy_item"
          >
            <div class="policy_item__info">
              <div class="policy_item__label">
                {{ policyName(code) }}
                <el-tag
                  v-if="!effectiveMap[code]"
                  size="small"
                  type="info"
                  effect="plain"
                >
                  暂无生效版本
                </el-tag>
              </div>
              <div class="policy_item__desc">
                {{ group.descs[code] }}
              </div>
            </div>
            <div class="policy_item__controls">
              <span
                v-for="field in fieldsOf(code)"
                :key="field.key"
                class="policy_item__field"
              >
                <el-input-number
                  v-model="form[group.key][code][field.key]"
                  :min="field.min"
                  :max="field.max"
                  :step="1"
                  step-strictly
                  controls-position="right"
                  style="width: 130px"
                />
                <span class="policy_item__unit">{{ field.unit }}</span>
              </span>
            </div>
          </div>
        </div>

        <footer class="policy_group__footer">
          <el-button
            :disabled="!dirtyMap[group.key] || saving"
            @click="resetGroup(group.key)"
          >
            重置
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!dirtyMap[group.key]"
            @click="saveGroup(group)"
          >
            保存修改
          </el-button>
        </footer>
      </section>
    </div>

    <!-- 历史版本抽屉 -->
    <el-drawer
      v-model="historyVisible"
      title="历史版本"
      size="720px"
    >
      <el-table
        v-loading="historyLoading"
        :data="historyRows"
        size="small"
      >
        <el-table-column
          prop="policyCode"
          label="编码"
          width="210"
        />
        <el-table-column
          label="版本"
          width="60"
        >
          <template #default="{ row }">
            v{{ row.policyVersion }}
          </template>
        </el-table-column>
        <el-table-column
          label="参数"
          min-width="150"
        >
          <template #default="{ row }">
            <span
              v-for="(text, i) in formatConfig(row)"
              :key="i"
              class="policy_page__config_item"
            >{{ text }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="生效期（上海）"
          width="195"
        >
          <template #default="{ row }">
            <span>{{ formatInstant(row.effectiveFrom) }}</span>
            <br>
            <span class="t-hint">至 {{ row.effectiveTo ? formatInstant(row.effectiveTo) : '长期' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="80"
        >
          <template #default="{ row }">
            <el-tag
              :type="statusOf(row).type"
              effect="plain"
            >
              {{ statusOf(row).label }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="policy_page__history_pager">
        <el-pagination
          v-model:current-page="historyQuery.pageNum"
          v-model:page-size="historyQuery.pageSize"
          :total="historyTotal"
          layout="total, prev, pager, next"
          @current-change="loadHistory"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Clock, Lock, ChatDotRound, Timer } from '@element-plus/icons-vue'
import { listPolicySchemas, pagePolicies, publishPolicy } from '@/api/system/policy'
import { withSafeAuth } from '@/utils/safe_auth'
import { formatInstant } from '@/utils/datetime'

// 分组定义：codes 与后端 PolicySchemas 登记的编码一致
const GROUPS = [
  {
    key: 'login',
    icon: Lock,
    title: '登录安全',
    subtitle: '账号密码登录的失败锁定策略',
    codes: ['LOGIN_MAX_FAIL', 'LOGIN_LOCK_MINUTES'],
    descs: {
      LOGIN_MAX_FAIL: '连续输错密码达到阈值后锁定账号',
      LOGIN_LOCK_MINUTES: '账号被锁定后自动解锁所需的时长'
    }
  },
  {
    key: 'sms',
    icon: ChatDotRound,
    title: '短信验证码',
    subtitle: '验证码有效期与发送频率限制',
    codes: ['SMS_CODE_TTL_SECONDS', 'SMS_RESEND_INTERVAL_SECONDS', 'SMS_DAILY_LIMIT', 'SMS_CODE_MAX_FAIL'],
    descs: {
      SMS_CODE_TTL_SECONDS: '验证码发出后可使用的时间',
      SMS_RESEND_INTERVAL_SECONDS: '同一手机号两次发送之间的最小间隔',
      SMS_DAILY_LIMIT: '同一手机号每天可接收的验证码条数',
      SMS_CODE_MAX_FAIL: '同一验证码连续输错达到次数后作废'
    }
  },
  {
    key: 'session',
    icon: Timer,
    title: '会话管理',
    subtitle: '登录会话的有效期',
    codes: ['SESSION_TIMEOUT_STUDENT_TEACHER', 'SESSION_TIMEOUT_ADMIN'],
    descs: {
      SESSION_TIMEOUT_STUDENT_TEACHER: '学生与老师端登录后保持在线的时长',
      SESSION_TIMEOUT_ADMIN: '后台管理端登录后保持在线的时长'
    }
  }
]

const loading = ref(false)
const saving = ref(false)
const schemas = ref({})
// 每个编码当前生效行（无生效版本则缺省）
const effectiveMap = ref({})
// 每个编码的名称（取最新版本行的 policyName）
const nameMap = ref({})
// 编辑态：form[groupKey][code][fieldKey]
const form = reactive({})
// 基线快照，用于脏检测与重置
const baseline = ref({})

function fieldsOf(code) {
  return schemas.value[code] || []
}

function policyName(code) {
  return nameMap.value[code] || code
}

function parseConfig(config) {
  try {
    return JSON.parse(config)
  } catch {
    return null
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pagePolicies({ pageNum: 1, pageSize: 100 })
    // 列表按编码升序、版本倒序：每码首行为最新版本
    const latestByCode = new Map()
    for (const row of data.records) {
      if (!latestByCode.has(row.policyCode)) {
        latestByCode.set(row.policyCode, row)
      }
    }
    const eff = {}
    const names = {}
    const base = {}
    const now = Date.now()
    for (const [code, row] of latestByCode) {
      names[code] = row.policyName
      const all = data.records.filter((r) => r.policyCode === code)
      const effective = all.find((r) => statusOf(r, now).label === '生效中') || row
      eff[code] = statusOf(effective, now).label === '生效中' ? effective : null
      const cfg = parseConfig(effective.config) || {}
      const values = {}
      for (const f of fieldsOf(code)) {
        values[f.key] = Number.isInteger(cfg[f.key]) ? cfg[f.key] : f.min
      }
      base[code] = values
    }
    effectiveMap.value = eff
    nameMap.value = names
    baseline.value = base
    applyBaseline()
  } finally {
    loading.value = false
  }
}

function applyBaseline() {
  for (const group of GROUPS) {
    if (!form[group.key]) form[group.key] = reactive({})
    for (const code of group.codes) {
      form[group.key][code] = reactive({ ...(baseline.value[code] || {}) })
    }
  }
}

const dirtyMap = computed(() => {
  const map = {}
  for (const group of GROUPS) {
    map[group.key] = group.codes.some(
      (code) => JSON.stringify(form[group.key]?.[code] || {}) !== JSON.stringify(baseline.value[code] || {})
    )
  }
  return map
})

function resetGroup(groupKey) {
  const group = GROUPS.find((g) => g.key === groupKey)
  for (const code of group.codes) {
    Object.assign(form[groupKey][code], baseline.value[code] || {})
  }
}

function statusOf(row, nowTs) {
  if (!row.enabled) return { label: '已停用', type: 'warning' }
  const now = nowTs ?? Date.now()
  const from = Date.parse(row.effectiveFrom)
  const to = row.effectiveTo ? Date.parse(row.effectiveTo) : null
  if (from > now) return { label: '未开始', type: 'info' }
  if (to !== null && to <= now) return { label: '已过期', type: 'info' }
  return { label: '生效中', type: 'success' }
}

/** 保存分组：变更的编码逐个发布新版本（立即生效），仅一次二级认证 */
async function saveGroup(group) {
  const changed = group.codes.filter(
    (code) => JSON.stringify(form[group.key][code]) !== JSON.stringify(baseline.value[code] || {})
  )
  if (changed.length === 0) return
  saving.value = true
  try {
    const res = await withSafeAuth(async () => {
      for (const code of changed) {
        await publishPolicy({
          policyCode: code,
          policyName: policyName(code),
          effectiveFrom: new Date().toISOString(),
          effectiveTo: null,
          config: JSON.stringify(form[group.key][code])
        })
      }
    })
    if (!res.ok) return
    ElMessage.success(`已保存并发布 ${changed.length} 个新版本`)
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 历史版本 ----------
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRows = ref([])
const historyTotal = ref(0)
const historyQuery = reactive({ pageNum: 1, pageSize: 20 })

async function loadHistory() {
  historyLoading.value = true
  try {
    const data = await pagePolicies({ ...historyQuery })
    historyRows.value = data.records
    historyTotal.value = data.total
  } finally {
    historyLoading.value = false
  }
}

function openHistory() {
  historyVisible.value = true
  historyQuery.pageNum = 1
  loadHistory()
}

/** 参数列格式化：按元数据展示「中文名：值 单位」 */
function formatConfig(row) {
  const fields = fieldsOf(row.policyCode)
  const parsed = parseConfig(row.config)
  if (!fields.length || !parsed || typeof parsed !== 'object') {
    return [row.config]
  }
  return fields.map((f) => `${f.label}：${parsed[f.key] ?? '-'} ${f.unit}`)
}

onMounted(async () => {
  schemas.value = await listPolicySchemas()
  await load()
})
</script>

<style scoped>
.policy_page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.policy_page__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.policy_page__subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.policy_page__groups {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.policy_group {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.policy_group__header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.policy_group__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.policy_group__title {
  font-size: 15px;
  font-weight: 600;
}

.policy_group__subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.policy_group__body {
  padding: 0 20px;
}

.policy_item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.policy_item:last-child {
  border-bottom: none;
}

.policy_item__label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
}

.policy_item__desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.policy_item__controls {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.policy_item__field {
  display: inline-flex;
  align-items: center;
}

.policy_item__unit {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.policy_group__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.policy_page__config_item {
  display: block;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.policy_page__history_pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
