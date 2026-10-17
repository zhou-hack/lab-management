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
            <code class="policy_page__config">{{ row.config }}</code>
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
        <el-form-item
          label="策略参数"
          prop="config"
        >
          <el-input
            v-model="publishForm.config"
            type="textarea"
            :rows="5"
            placeholder="JSON 对象，如 {&quot;maxFailCount&quot;:5}"
          />
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pagePolicies, publishPolicy } from '@/api/system/policy'
import { withSafeAuth } from '@/utils/safe_auth'
import { formatInstant, toUtcIso } from '@/utils/datetime'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const submitting = ref(false)

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

// ---------- 发布 ----------
const publishVisible = ref(false)
const publishFormRef = ref()
const publishForm = reactive({ policyCode: '', policyName: '', effectiveFrom: '', effectiveTo: '', config: '' })
const codeOptions = ref([])

const publishRules = {
  policyCode: [{ required: true, message: '请选择策略编码', trigger: 'change' }],
  policyName: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  effectiveFrom: [{ required: true, message: '请选择生效开始', trigger: 'change' }],
  config: [
    { required: true, message: '请输入策略参数', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        try {
          const parsed = JSON.parse(value)
          if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
            callback(new Error('必须为 JSON 对象'))
          } else {
            callback()
          }
        } catch {
          callback(new Error('不是合法 JSON'))
        }
      },
      trigger: 'blur'
    }
  ]
}

function onCodeChange(code) {
  const hit = codeOptions.value.find((c) => c.policyCode === code)
  if (hit) publishForm.policyName = hit.policyName
}

async function openPublish() {
  publishForm.policyCode = ''
  publishForm.policyName = ''
  publishForm.effectiveFrom = ''
  publishForm.effectiveTo = ''
  publishForm.config = ''
  publishVisible.value = true
  // 编码选项取全部编码的最新版本名称
  const data = await pagePolicies({ pageNum: 1, pageSize: 100 })
  const seen = new Map()
  for (const item of data.records) {
    if (!seen.has(item.policyCode)) {
      seen.set(item.policyCode, { policyCode: item.policyCode, policyName: item.policyName })
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
  let payload
  try {
    payload = {
      policyCode: publishForm.policyCode,
      policyName: publishForm.policyName,
      effectiveFrom: toUtcIso(publishForm.effectiveFrom),
      effectiveTo: publishForm.effectiveTo ? toUtcIso(publishForm.effectiveTo) : null,
      config: publishForm.config
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

onMounted(load)
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

.policy_page__config {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.policy_page__tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
}
</style>
