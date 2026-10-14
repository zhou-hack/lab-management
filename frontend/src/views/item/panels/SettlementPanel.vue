<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="query.issueLineId"
        clearable
        class="w240"
        placeholder="按原出库行 ID 过滤"
        @change="pager.search"
      />
      <el-button
        v-permission="'item:settlement:create'"
        type="primary"
        @click="openCreate"
      >
        核销登记
      </el-button>
      <el-button @click="pager.search">
        查询
      </el-button>
    </div>

    <el-table
      v-loading="pager.loading"
      :data="pager.rows"
      border
    >
      <el-table-column
        prop="id"
        label="结算 ID"
        width="190"
        show-overflow-tooltip
      />
      <el-table-column
        prop="issueLineId"
        label="原出库行 ID"
        width="190"
        show-overflow-tooltip
      />
      <el-table-column
        label="类型"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.settlementType"
            :map="settlementTypeMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="数量"
        width="100"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.quantity) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="returnLineId"
        label="关联归还行 ID"
        width="190"
        show-overflow-tooltip
      />
      <el-table-column
        prop="reason"
        label="原因"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="结算时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.settledAt) }}
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 核销登记 -->
    <el-dialog
      v-model="createVisible"
      title="核销登记（消耗 / 丢失 / 损坏）"
      width="560px"
    >
      <el-form label-width="110px">
        <el-form-item
          label="原出库行 ID"
          required
        >
          <el-input
            v-model="form.issueLineId"
            placeholder="从领用出库单详情复制行 ID"
          />
        </el-form-item>
        <el-form-item
          label="核销类型"
          required
        >
          <el-select
            v-model="form.settlementType"
            class="w240"
          >
            <el-option
              v-for="(cfg, value) in createTypes"
              :key="value"
              :label="cfg.label"
              :value="value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="数量"
          required
        >
          <el-input-number
            v-model="form.quantity"
            :min="0"
            :step="1"
            :precision="3"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="原因">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="2"
            placeholder="数量差异需说明原因"
          />
        </el-form-item>
      </el-form>
      <div class="muted small">
        累计核销量不能超过原出库量；归还入库过账时会自动生成归还结算记录。
      </div>
      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCreate"
        >
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createSettlement, pageSettlements } from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { settlementTypeMap } from '@/utils/statusMap'
import { fmtShanghai, formatQty } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'

const query = reactive({ issueLineId: '' })

/** 手工登记类型：归还 RETURNED 由归还入库过账自动生成，不在登记下拉中提供 */
const createTypes = computed(() =>
  Object.fromEntries(Object.entries(settlementTypeMap).filter(([value]) => value !== 'RETURNED'))
)

const pager = usePagedList((params) =>
  pageSettlements({
    ...params,
    issueLineId: query.issueLineId || undefined
  })
)

const createVisible = ref(false)
const submitting = ref(false)
const form = reactive({ issueLineId: '', settlementType: 'CONSUMED', quantity: null, reason: '' })

function openCreate() {
  form.issueLineId = ''
  form.settlementType = 'CONSUMED'
  form.quantity = null
  form.reason = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!form.issueLineId || !/^\d{1,19}$/.test(form.issueLineId)) {
    ElMessage.warning('原出库行 ID 必须为纯数字')
    return
  }
  if (form.quantity === null || form.quantity <= 0) {
    ElMessage.warning('核销数量必须大于 0')
    return
  }
  submitting.value = true
  try {
    await createSettlement({
      issueLineId: form.issueLineId,
      settlementType: form.settlementType,
      quantity: form.quantity,
      reason: form.reason || undefined
    })
    ElMessage.success('核销成功')
    createVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(pager.load)

defineExpose({ refresh: pager.load })
</script>
