<template>
  <div class="audit_page">
    <!-- 筛选栏 -->
    <el-card class="audit_page__card">
      <div class="audit_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="老师 / 学生姓名 / 学工号"
              clearable
              style="width: 220px"
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
      </div>
    </el-card>

    <!-- 审计表格（只读） -->
    <el-card class="audit_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          prop="teacherName"
          label="授权老师"
          min-width="110"
        />
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
          label="撤销时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ row.revokedAt ? formatInstant(row.revokedAt) : '—' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="reason"
          label="授权说明"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.reason || '—' }}
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无授权记录" />
        </template>
      </el-table>
      <div class="audit_page__pager">
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
import { pageGrantAudit } from '@/api/grant'
import { formatInstant } from '@/utils/datetime'

const query = reactive({ pageNum: 1, pageSize: 20, keyword: '', status: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)

const statusLabel = (s) => ({ ACTIVE: '生效中', REVOKED: '已撤销', EXPIRED: '已过期' })[s] || s
const statusTag = (s) => ({ ACTIVE: 'success', REVOKED: 'danger', EXPIRED: 'info' })[s] || 'info'

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.keyword) params.keyword = undefined
    if (!params.status) params.status = undefined
    const data = await pageGrantAudit(params)
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

onMounted(load)
</script>

<style scoped>
.audit_page__card + .audit_page__card {
  margin-top: 14px;
}

.audit_page__filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.audit_page__filter :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}

.audit_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
