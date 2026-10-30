<template>
  <div class="consumable_stock">
    <el-alert
      class="consumable_stock__notice"
      type="info"
      :closable="false"
      :title="CURRENT_STOCK_AVAILABILITY_NOTICE"
    />
    <div class="card consumable_stock__card">
      <div class="consumable_stock__toolbar">
        <el-input
          v-model="query.materialName"
          placeholder="耗材名称"
          clearable
          class="consumable_stock__name"
          @keyup.enter="search"
          @clear="search"
        />
        <item-location-select
          v-model="query.locationId"
          class="consumable_stock__location"
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
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        row-key="id"
      >
        <el-table-column
          prop="materialName"
          label="耗材名称"
          min-width="150"
        />
        <el-table-column
          label="规格型号"
          min-width="130"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.specification || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="库位"
          min-width="140"
        >
          <template #default="{ row }">
            <div>{{ row.locationName || '—' }}</div>
            <div class="t-hint">
              {{ row.locationCode || '' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="库内位置"
          min-width="130"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.positionText || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="保有数量"
          min-width="115"
          align="right"
        >
          <template #default="{ row }">
            <item-quantity-text
              :value="row.ownedQuantity"
              :unit="row.baseUnit"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="借出数量"
          min-width="180"
          align="right"
        >
          <template #default="{ row }">
            <item-quantity-text
              :value="row.loanedQuantity"
              :unit="row.baseUnit"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="库内实际可用"
          min-width="155"
          align="right"
        >
          <template #default="{ row }">
            <item-quantity-text
              :value="row.availableQuantity"
              :unit="row.baseUnit"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="更新时间"
          min-width="155"
        >
          <template #default="{ row }">
            {{ formatInstant(row.updateTime) || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="90"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openPositionDialog(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            description="暂无符合条件的耗材库存"
            :image-size="64"
          />
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="consumable_stock__pager"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @size-change="onSizeChange"
        @current-change="loadData"
      />
    </div>

    <el-dialog
      v-model="positionDialogVisible"
      title="编辑库内位置"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-descriptions
        :column="1"
        border
        class="consumable_stock__summary"
      >
        <el-descriptions-item label="耗材">
          {{ editingRow?.materialName }}
        </el-descriptions-item>
        <el-descriptions-item label="所属库位">
          {{ editingRow?.locationName }}
        </el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="库内位置">
          <el-input
            v-model="positionText"
            maxlength="256"
            placeholder="请输入库内位置，留空表示未指定"
            clearable
            @keyup.enter="savePosition"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="positionDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="positionSaving"
          @click="savePosition"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import { getBalances, updateBalancePosition } from '@/api/item/stock'
import { formatInstant } from '@/utils/datetime'
import { CURRENT_STOCK_AVAILABILITY_NOTICE } from '../inventory.rules'

const query = reactive({ materialName: '', locationId: '', pageNum: 1, pageSize: 20 })
const records = ref([])
const total = ref(0)
const loading = ref(false)
const positionDialogVisible = ref(false)
const positionSaving = ref(false)
const editingRow = ref(null)
const positionText = ref('')

function openPositionDialog(row) {
  editingRow.value = row
  positionText.value = row.positionText || ''
  positionDialogVisible.value = true
}

async function savePosition() {
  if (!editingRow.value || positionSaving.value) return
  positionSaving.value = true
  try {
    await updateBalancePosition(editingRow.value.id, {
      positionText: positionText.value.trim() || null,
      version: editingRow.value.version
    })
    ElMessage.success('库内位置已更新')
    positionDialogVisible.value = false
    await loadData()
  } catch {
    // 请求层已展示位置冲突、权限或版本错误
  } finally {
    positionSaving.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const result = await getBalances({
      ...query,
      materialName: query.materialName.trim() || undefined,
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
  Object.assign(query, { materialName: '', locationId: '', pageNum: 1, pageSize: 20 })
  loadData()
}

function onSizeChange() {
  query.pageNum = 1
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.consumable_stock__notice {
  margin-bottom: 14px;
}

.consumable_stock__card {
  padding: 18px;
}

.consumable_stock__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  overflow-x: auto;
}

.consumable_stock__toolbar > * {
  flex-shrink: 0;
}

.consumable_stock__name,
.consumable_stock__location {
  width: 200px;
}

.consumable_stock__summary {
  margin-bottom: 16px;
}

.consumable_stock__pager {
  justify-content: flex-end;
  margin-top: 16px;
  overflow-x: auto;
}

@media (max-width: 768px) {
  .consumable_stock__card {
    padding: 14px;
  }
}
</style>
