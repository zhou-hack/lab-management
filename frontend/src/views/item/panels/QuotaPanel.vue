<template>
  <div>
    <div class="toolbar">
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
        prop="specification"
        label="规格型号"
        min-width="160"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.specification || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="categoryName"
        label="分类"
        width="120"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.categoryName || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="每人每课时用量"
        width="150"
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

    <!-- 批量登记（全局定额：同物料覆盖更新） -->
    <el-dialog
      v-model="upsertVisible"
      title="批量登记定额（同一物料已有定额则覆盖更新）"
      width="980px"
    >
      <el-form label-width="120px">
        <el-form-item label="物料分类">
          <CategorySelect
            v-model="upsertCategory"
            all-option
            class="w240"
          />
          <span class="muted small ml8">先选分类缩小范围，再在行内搜索物料</span>
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
              :category-id="upsertCategory"
              placeholder="搜索物料"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="规格型号"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ (row.skuId && skuDetails[row.skuId]?.specification) || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          label="分类"
          width="130"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ (row.skuId && skuDetails[row.skuId]?.categoryName) || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          label="每人每课时用量"
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
        物料预约登记时按 参与人数 × 课时数 × 每人每课时用量 生成快照，超量仅标记需额外审批。
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
        <el-form-item label="每人每课时用量">
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
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { disableQuota, enableQuota, getMaterialSku, pageQuotas, updateQuota, upsertQuota } from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { formatQty } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import SkuSelect from '@/components/SkuSelect.vue'
import CategorySelect from '@/components/CategorySelect.vue'

const query = reactive({ categoryId: null, skuId: null })

const pager = usePagedList((params) =>
  pageQuotas({
    ...params,
    categoryId: query.categoryId || undefined,
    skuId: query.skuId || undefined
  })
)

const upsertVisible = ref(false)
const submitting = ref(false)
const upsertForm = reactive({ lines: [] })
/** 批量登记弹窗的物料分类过滤（全局定额，无实例维度） */
const upsertCategory = ref(null)

/** 批量登记弹窗内选中 SKU 的详情缓存（列表同步展示 规格型号 / 分类 用） */
const skuDetails = reactive({})

watch(
  () => upsertForm.lines.map((l) => l.skuId),
  (ids) => {
    for (const id of ids) {
      if (id && !skuDetails[id]) {
        getMaterialSku(id)
          .then((sku) => {
            skuDetails[id] = sku
          })
          .catch(() => {})
      }
    }
  }
)

const updateVisible = ref(false)
const updateId = ref(null)
const updateQty = ref(0)

function openUpsert() {
  upsertCategory.value = null
  upsertForm.lines = [{ skuId: null, qtyPerStudentPeriod: 0 }]
  upsertVisible.value = true
}

async function submitUpsert() {
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
      : `确认停用「${row.skuName}」的领用定额？停用后新物料预约不再计算该物料快照。`
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
