<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="query.offeringId"
        clearable
        class="w220"
        placeholder="开课实例 ID 过滤"
        @change="pager.search"
      />
      <CategorySelect
        v-model="query.categoryId"
        all-option
        @change="pager.search"
      />
      <SkuSelect
        v-model="query.skuId"
        all-option
        @change="pager.search"
      />
      <el-button
        v-permission="'item:quota:create'"
        type="primary"
        @click="openUpsert"
      >
        批量登记定额
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
        label="定额 ID"
        width="190"
        show-overflow-tooltip
      />
      <el-table-column
        prop="offeringId"
        label="开课实例 ID"
        width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="物料"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <div>{{ row.skuName }}（{{ row.baseUnit }}）</div>
          <div class="muted small">
            {{ row.skuCode }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="每人每课时定额"
        width="140"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.qtyPerStudentPeriod) }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="200"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-permission="'item:quota:update'"
            link
            type="primary"
            @click="openUpdate(row)"
          >
            修改定额
          </el-button>
          <el-button
            v-if="row.enabled"
            v-permission="'item:quota:enable'"
            link
            type="warning"
            @click="doToggle(row, false)"
          >
            停用
          </el-button>
          <el-button
            v-else
            v-permission="'item:quota:enable'"
            link
            type="success"
            @click="doToggle(row, true)"
          >
            启用
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 批量登记（按开课实例整体覆盖） -->
    <el-dialog
      v-model="upsertVisible"
      title="批量登记定额（同一开课实例整组覆盖）"
      width="820px"
    >
      <el-form label-width="120px">
        <el-form-item
          label="开课实例 ID"
          required
        >
          <el-input
            v-model="upsertForm.offeringId"
            class="w240"
            placeholder="纯数字 ID"
          />
        </el-form-item>
      </el-form>
      <el-table
        :data="upsertForm.lines"
        border
      >
        <el-table-column
          label="物料"
          min-width="240"
        >
          <template #default="{ row }">
            <SkuSelect
              v-model="row.skuId"
              placeholder="搜索物料"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="每人每课时定额"
          width="200"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.qtyPerStudentPeriod"
              :min="0"
              :step="0.1"
              :precision="3"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="80"
        >
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              @click="upsertForm.lines.splice($index, 1)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button
        class="mt8"
        @click="upsertForm.lines.push({ skuId: null, qtyPerStudentPeriod: 0 })"
      >
        + 添加行
      </el-button>
      <div class="muted small mt8">
        申领单登记时按 参与人数 × 课时数 × 每人每课时定额 生成快照，超定额仅标记需额外审批。
      </div>
      <template #footer>
        <el-button @click="upsertVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitUpsert"
        >
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 单条修改 -->
    <el-dialog
      v-model="updateVisible"
      title="修改定额"
      width="480px"
    >
      <el-form label-width="140px">
        <el-form-item label="每人每课时定额">
          <el-input-number
            v-model="updateQty"
            :min="0"
            :step="0.1"
            :precision="3"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="updateVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitUpdate"
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
import { disableQuota, enableQuota, pageQuotas, updateQuota, upsertQuota } from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { formatQty } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import SkuSelect from '@/components/SkuSelect.vue'
import CategorySelect from '@/components/CategorySelect.vue'

const query = reactive({ offeringId: '', categoryId: null, skuId: null })

const pager = usePagedList((params) =>
  pageQuotas({
    ...params,
    offeringId: query.offeringId || undefined,
    categoryId: query.categoryId || undefined,
    skuId: query.skuId || undefined
  })
)

const upsertVisible = ref(false)
const submitting = ref(false)
const upsertForm = reactive({ offeringId: '', lines: [] })

const updateVisible = ref(false)
const updateId = ref(null)
const updateQty = ref(0)

function openUpsert() {
  upsertForm.offeringId = ''
  upsertForm.lines = [{ skuId: null, qtyPerStudentPeriod: 0 }]
  upsertVisible.value = true
}

async function submitUpsert() {
  if (!upsertForm.offeringId || !/^\d{1,19}$/.test(upsertForm.offeringId)) {
    ElMessage.warning('开课实例 ID 必须为纯数字')
    return
  }
  if (!upsertForm.lines.length) {
    ElMessage.warning('请至少添加一行物料')
    return
  }
  for (const line of upsertForm.lines) {
    if (!line.skuId) {
      ElMessage.warning('存在未选择物料的行')
      return
    }
  }
  submitting.value = true
  try {
    await upsertQuota({
      offeringId: upsertForm.offeringId,
      lines: upsertForm.lines.map((l) => ({ skuId: l.skuId, qtyPerStudentPeriod: l.qtyPerStudentPeriod }))
    })
    ElMessage.success('定额已登记')
    upsertVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

function openUpdate(row) {
  updateId.value = row.id
  updateQty.value = row.qtyPerStudentPeriod
  updateVisible.value = true
}

async function submitUpdate() {
  submitting.value = true
  try {
    await updateQuota(updateId.value, { qtyPerStudentPeriod: updateQty.value })
    ElMessage.success('已保存')
    updateVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doToggle(row, enable) {
  const ok = await confirmAction(
    enable
      ? `确认启用「${row.skuName}」的领用定额？`
      : `确认停用「${row.skuName}」的领用定额？停用后新申领单不再计算该物料快照。`
  )
  if (!ok) return
  try {
    if (enable) {
      await enableQuota(row.id)
      ElMessage.success('已启用')
    } else {
      await disableQuota(row.id)
      ElMessage.success('已停用')
    }
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

onMounted(() => {
  pager.load()
})

defineExpose({ refresh: pager.load })
</script>
