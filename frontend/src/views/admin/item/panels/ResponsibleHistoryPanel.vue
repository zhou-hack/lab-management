<template>
  <div class="responsible_history card">
    <div class="responsible_history__toolbar">
      <item-location-select
        v-model="query.locationId"
        class="responsible_history__location"
        placeholder="全部库位"
        @change="search"
      />
      <el-button
        type="primary"
        @click="search"
      >
        查询
      </el-button>
      <el-button @click="reset">
        重置
      </el-button>
      <span class="t-hint">每次库位负责人实际发生变化时记录一条，固定资产随所在库位同步。</span>
    </div>

    <el-table
      v-loading="loading"
      :data="records"
      row-key="id"
    >
      <el-table-column
        label="变动时间"
        min-width="170"
      >
        <template #default="{ row }">
          {{ formatInstant(row.occurredAt, { withSeconds: true }) || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="库位"
        min-width="170"
      >
        <template #default="{ row }">
          <div>{{ row.locationName || '—' }}</div>
          <div class="t-hint">
            {{ row.locationCode || '' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="原负责人"
        min-width="190"
      >
        <template #default="{ row }">
          {{ adminNames(row.beforeAdmins) }}
        </template>
      </el-table-column>
      <el-table-column
        label="新负责人"
        min-width="190"
      >
        <template #default="{ row }">
          {{ adminNames(row.afterAdmins) }}
        </template>
      </el-table-column>
      <el-table-column
        label="变动人"
        min-width="120"
      >
        <template #default="{ row }">
          {{ row.operatorName || '—' }}
        </template>
      </el-table-column>
      <template #empty>
        <el-empty
          description="暂无负责人变动记录"
          :image-size="64"
        />
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      class="responsible_history__pager"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      background
      @size-change="onSizeChange"
      @current-change="loadData"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import { getResponsibleHistory } from '@/api/item/stock'
import { formatInstant } from '@/utils/datetime'

const query = reactive({ locationId: '', pageNum: 1, pageSize: 10 })
const records = ref([])
const total = ref(0)
const loading = ref(false)

function adminNames(admins) {
  return (admins || [])
    .map((admin) => `${admin.realName || admin.userId}${admin.accountName ? `（${admin.accountName}）` : ''}`)
    .join('、') || '未分配'
}

async function loadData() {
  loading.value = true
  try {
    const result = await getResponsibleHistory({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      locationId: query.locationId || undefined
    })
    records.value = result?.records || []
    total.value = Number(result?.total || 0)
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadData()
}

function reset() {
  Object.assign(query, { locationId: '', pageNum: 1, pageSize: 10 })
  loadData()
}

function onSizeChange() {
  query.pageNum = 1
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.responsible_history {
  padding: 18px;
}

.responsible_history__toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.responsible_history__location {
  width: 280px;
}

.responsible_history__pager {
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}
</style>
