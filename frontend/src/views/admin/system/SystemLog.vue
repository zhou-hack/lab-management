<template>
  <div class="log_page">
    <!-- 筛选栏 -->
    <el-card class="log_page__card">
      <div class="log_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="操作者 / 模块 / 动作码"
              clearable
              style="width: 200px"
              @keyup.enter="search"
              @clear="search"
            />
          </el-form-item>
          <el-form-item label="模块">
            <el-select
              v-model="query.moduleCode"
              placeholder="全部"
              clearable
              style="width: 130px"
              @change="search"
            >
              <el-option
                label="认证"
                value="auth"
              />
              <el-option
                label="系统管理"
                value="system"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="结果">
            <el-select
              v-model="query.success"
              placeholder="全部"
              clearable
              style="width: 110px"
              @change="search"
            >
              <el-option
                label="成功"
                :value="1"
              />
              <el-option
                label="失败"
                :value="0"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="发生时间">
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              value-format="YYYY-MM-DD HH:mm:ss"
              start-placeholder="开始（上海时间）"
              end-placeholder="结束（上海时间）"
              style="width: 340px"
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
      </div>
    </el-card>

    <!-- 日志表格 -->
    <el-card class="log_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          label="发生时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作者"
          min-width="150"
        >
          <template #default="{ row }">
            <span>{{ row.actorName || '-' }}</span>
            <span class="log_page__actor_type">{{ actorTypeLabel(row.actorType) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="moduleCode"
          label="模块"
          width="90"
        />
        <el-table-column
          prop="actionCode"
          label="动作"
          min-width="190"
          show-overflow-tooltip
        />
        <el-table-column
          label="目标"
          min-width="190"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.targetTable }}{{ row.targetId ? ` #${row.targetId}` : '' }}
          </template>
        </el-table-column>
        <el-table-column
          label="结果"
          width="80"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.success === 1 ? 'success' : 'danger'"
              size="small"
            >
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="clientIp"
          label="来源 IP"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.clientIp || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="requestId"
          label="请求 ID"
          min-width="150"
          show-overflow-tooltip
        />
        <template #empty>
          <el-empty description="暂无日志" />
        </template>
      </el-table>
      <div class="log_page__pager">
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageLogs } from '@/api/system/log'
import { formatInstant, toUtcIso } from '@/utils/datetime'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', moduleCode: '', success: null })
const timeRange = ref(null)
const rows = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.keyword) params.keyword = query.keyword
    if (query.moduleCode) params.moduleCode = query.moduleCode
    if (query.success === 0 || query.success === 1) params.success = query.success
    if (timeRange.value && timeRange.value.length === 2) {
      // 下界含、上界不含：上界取所选结束秒的下一秒
      params.startAt = toUtcIso(timeRange.value[0])
      const endGuess = Date.parse(toUtcIso(timeRange.value[1])) + 1000
      params.endAt = new Date(endGuess).toISOString()
    }
    const data = await pageLogs(params)
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    ElMessage.error(e.message || '查询失败')
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
  query.moduleCode = ''
  query.success = null
  timeRange.value = null
  search()
}

function actorTypeLabel(type) {
  const map = { STUDENT: '学生', TEACHER: '老师', ADMIN: '管理员', SYSTEM: '系统', UNKNOWN: '未知' }
  return map[type] ? `· ${map[type]}` : ''
}

onMounted(load)
</script>

<style scoped>
.log_page__card + .log_page__card {
  margin-top: 14px;
}

.log_page__filter :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}

.log_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.log_page__actor_type {
  margin-left: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
