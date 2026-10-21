<template>
  <div class="policy_page">
    <!-- 筛选栏 -->
    <el-card class="policy_page__card">
      <div class="policy_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="策略编码 / 名称"
              clearable
              style="width: 220px"
              @keyup.enter="search"
              @clear="search"
            />
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
        <div v-permission="'system:policy:publish'">
          <el-button
            type="primary"
            @click="openPublish"
          >
            发布新版本
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 策略表格 -->
    <el-card class="policy_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          prop="policyCode"
          label="策略编码"
          min-width="210"
        />
        <el-table-column
          label="版本"
          width="70"
        >
          <template #default="{ row }">
            v{{ row.policyVersion }}
          </template>
        </el-table-column>
        <el-table-column
          prop="policyName"
          label="策略名称"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          label="参数"
          min-width="170"
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
          label="生效区间"
          min-width="235"
        >
          <template #default="{ row }">
            {{ formatInstant(row.effectiveFrom) }} ~ {{ row.effectiveTo ? formatInstant(row.effectiveTo) : '长期' }}
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
          label="发布时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.createTime) }}
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无策略" />
        </template>
      </el-table>
      <div class="policy_page__pager">
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

    <!-- 发布新版本 -->
    <el-dialog
      v-model="publishVisible"
      title="发布策略新版本"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="publishFormRef"
        :model="publishForm"
        :rules="publishRules"
        label-width="96px"
      >
        <el-form-item
          label="策略编码"
          prop="policyCode"
        >
          <el-select
            v-model="publishForm.policyCode"
            placeholder="选择已登记编码"
            style="width: 100%"
            @change="onCodeChange"
          >
            <el-option
              v-for="c in codeOptions"
              :key="c.policyCode"
              :label="`${c.policyCode}（${c.policyName}）`"
              :value="c.policyCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="策略名称"
          prop="policyName"
        >
          <el-input v-model="publishForm.policyName" />
        </el-form-item>
        <el-form-item
          label="生效开始"
          prop="effectiveFrom"
        >
          <el-date-picker
            v-model="publishForm.effectiveFrom"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="上海本地时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="生效结束">
          <el-date-picker
            v-model="publishForm.effectiveTo"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空为长期"
            style="width: 100%"
          />
        </el-form-item>
        <template v-if="currentFields.length > 0">
          <el-form-item
            v-for="field in currentFields"
            :key="field.key"
            :label="field.label"
            required
          >
            <el-input-number
              v-model="publishForm.configFields[field.key]"
              :min="field.min"
              :max="field.max"
              :step="1"
              step-strictly
              controls-position="right"
              style="width: 180px"
            />
            <span class="policy_page__unit">{{ field.unit }}（{{ field.min }}-{{ field.max }}）</span>
          </el-form-item>
        </template>
        <el-form-item v-else-if="publishForm.policyCode">
          <el-text type="info">
            该编码未登记参数元数据
          </el-text>
        </el-form-item>
      </el-form>
      <p class="policy_page__tip t-hint">
        版本号按同码自动递增；同码生效区间不得重叠；发布后不可修改，仅可通过更高版本替代。
      </p>
      <template #footer>
        <el-button @click="publishVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitPublish"
        >
          发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listPolicySchemas, pagePolicies, publishPolicy } from '@/api/system/policy'
import { withSafeAuth } from '@/utils/safe_auth'
import { formatInstant, toUtcIso } from '@/utils/datetime'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

// 参数字段元数据：{ [policyCode]: [{ key, label, type, min, max, unit }] }
const schemas = ref({})

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.keyword) params.keyword = undefined
    const data = await pagePolicies(params)
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
  search()
}

function statusOf(row) {
  if (!row.enabled) return { label: '已停用', type: 'warning' }
  const now = Date.now()
  const from = Date.parse(row.effectiveFrom)
  const to = row.effectiveTo ? Date.parse(row.effectiveTo) : null
  if (from > now) return { label: '未开始', type: 'info' }
  if (to !== null && to <= now) return { label: '已过期', type: 'info' }
  return { label: '生效中', type: 'success' }
}

/** 参数列格式化：按元数据展示「中文名：值 单位」，无元数据回退原始 JSON */
function formatConfig(row) {
  const fields = schemas.value[row.policyCode]
  let parsed = null
  try {
    parsed = JSON.parse(row.config)
  } catch {
    parsed = null
  }
  if (!fields || !parsed || typeof parsed !== 'object') {
    return [row.config]
  }
  return fields.map((f) => `${f.label}：${parsed[f.key] ?? '-'} ${f.unit}`)
}

// ---------- 发布 ----------
const publishVisible = ref(false)
const publishFormRef = ref()
const publishForm = reactive({ policyCode: '', policyName: '', effectiveFrom: '', effectiveTo: '', configFields: {} })
const codeOptions = ref([])

const currentFields = computed(() => schemas.value[publishForm.policyCode] || [])

const publishRules = {
  policyCode: [{ required: true, message: '请选择策略编码', trigger: 'change' }],
  policyName: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  effectiveFrom: [{ required: true, message: '请选择生效开始', trigger: 'change' }]
}

function onCodeChange(code) {
  const hit = codeOptions.value.find((c) => c.policyCode === code)
  if (hit) publishForm.policyName = hit.policyName
  // 预填该编码当前最新版本的参数值；无则给元数据最小值
  const fields = schemas.value[code] || []
  const latest = rows.value.find((r) => r.policyCode === code) || hit
  let latestConfig = null
  if (latest && latest.config) {
    try {
      latestConfig = JSON.parse(latest.config)
    } catch {
      latestConfig = null
    }
  }
  const configFields = {}
  for (const f of fields) {
    const v = latestConfig ? latestConfig[f.key] : null
    configFields[f.key] = Number.isInteger(v) ? v : f.min
  }
  publishForm.configFields = configFields
}

async function openPublish() {
  publishForm.policyCode = ''
  publishForm.policyName = ''
  publishForm.effectiveFrom = ''
  publishForm.effectiveTo = ''
  publishForm.configFields = {}
  publishVisible.value = true
  // 编码选项取全部编码的最新版本（列表按编码升序、版本倒序，取每码首行）
  const data = await pagePolicies({ pageNum: 1, pageSize: 100 })
  const seen = new Map()
  for (const item of data.records) {
    if (!seen.has(item.policyCode)) {
      seen.set(item.policyCode, { policyCode: item.policyCode, policyName: item.policyName, config: item.config })
    }
  }
  codeOptions.value = [...seen.values()]
}

async function submitPublish() {
  try {
    await publishFormRef.value.validate()
  } catch {
    return
  }
  for (const f of currentFields.value) {
    if (!Number.isInteger(publishForm.configFields[f.key])) {
      ElMessage.error(`请填写${f.label}`)
      return
    }
  }
  let payload
  try {
    payload = {
      policyCode: publishForm.policyCode,
      policyName: publishForm.policyName,
      effectiveFrom: toUtcIso(publishForm.effectiveFrom),
      effectiveTo: publishForm.effectiveTo ? toUtcIso(publishForm.effectiveTo) : null,
      config: JSON.stringify(publishForm.configFields)
    }
  } catch (e) {
    ElMessage.error(e.message)
    return
  }
  submitting.value = true
  try {
    const res = await withSafeAuth(() => publishPolicy(payload))
    if (!res.ok) return
    ElMessage.success(`已发布 v${res.data}`)
    publishVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  load()
  schemas.value = await listPolicySchemas()
})
</script>

<style scoped>
.policy_page__card + .policy_page__card {
  margin-top: 14px;
}

.policy_page__filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.policy_page__filter :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}

.policy_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.policy_page__config_item {
  display: block;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.policy_page__unit {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.policy_page__tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
}
</style>
