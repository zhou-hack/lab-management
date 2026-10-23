<template>
  <div>
    <div class="toolbar">
      <LocationSelect
        v-model="query.locationId"
        placeholder="全部库位"
        all-option
        @change="pager.search"
      />
      <SkuSelect
        v-model="query.skuId"
        all-option
        @change="pager.search"
      />
      <el-checkbox
        v-model="warningOnly"
        @change="pager.search"
      >
        仅看预警
      </el-checkbox>
      <el-button @click="pager.search">
        查询
      </el-button>
      <el-button
        v-permission="'item:balance:import'"
        @click="importDialogRef.open()"
      >
        期初导入
      </el-button>
      <el-dropdown
        v-permission="'item:balance:export'"
        @command="doExport"
      >
        <el-button>
          导出<el-icon class="el-icon--right">
            <arrow-down />
          </el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="xlsx">
              Excel（.xlsx）
            </el-dropdown-item>
            <el-dropdown-item command="csv">
              CSV（.csv）
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-table
      v-loading="pager.loading"
      :data="pager.rows"
      border
    >
      <el-table-column
        prop="locationName"
        label="库位"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="物料"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <div>{{ row.skuName }}</div>
          <div class="muted small">
            {{ row.skuCode }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        prop="baseUnit"
        label="单位"
        width="70"
      />
      <el-table-column
        label="库存量"
        width="100"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.stockQty) }}
        </template>
      </el-table-column>
      <el-table-column
        label="安全库存"
        width="100"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.safeQty) }}
        </template>
      </el-table-column>
      <el-table-column
        label="补货目标"
        width="100"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.targetQty) }}
        </template>
      </el-table-column>
      <el-table-column
        label="建议补货"
        width="100"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.suggestedQty) }}
        </template>
      </el-table-column>
      <el-table-column
        label="预警"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.warningLevel || row.level"
            :map="warningLevelMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        v-if="!warningOnly"
        label="更新时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.updateTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="180"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-permission="['item:stock:create', 'item:stock:post']"
            link
            type="primary"
            @click="openAdjust(row)"
          >
            调整库存
          </el-button>
          <el-button
            v-permission="'item:balance:update'"
            link
            type="primary"
            @click="openWarning(row)"
          >
            预警配置
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 预警配置 -->
    <el-dialog
      v-model="warningVisible"
      title="预警配置"
      width="460px"
    >
      <el-descriptions
        v-if="warningRow"
        :column="2"
        border
        class="mb8"
      >
        <el-descriptions-item label="库位">
          {{ warningRow.locationName }}
        </el-descriptions-item>
        <el-descriptions-item label="物料">
          {{ warningRow.skuName }}
        </el-descriptions-item>
      </el-descriptions>
      <el-form label-width="100px">
        <el-form-item
          label="安全库存"
          required
        >
          <el-input-number
            v-model="warningForm.safeQty"
            :min="0"
            :step="1"
            :precision="3"
            controls-position="right"
          />
          <span class="muted small ml4">≤ 库存量时触发预警</span>
        </el-form-item>
        <el-form-item
          label="补货目标"
          required
        >
          <el-input-number
            v-model="warningForm.targetQty"
            :min="0"
            :step="1"
            :precision="3"
            controls-position="right"
          />
          <span class="muted small ml4">建议补货 = 目标 - 当前库存</span>
        </el-form-item>
      </el-form>
      <div class="muted small">
        补货目标不能低于安全库存；小数位数不能超过物料数量精度。
      </div>
      <template #footer>
        <el-button @click="warningVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitWarning"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 调整库存（差值生成 ADJUST 调账单并自动审批过账，立即生效） -->
    <el-dialog
      v-model="adjustVisible"
      title="调整库存"
      width="460px"
    >
      <el-descriptions
        v-if="adjustRow"
        :column="2"
        border
        class="mb8"
      >
        <el-descriptions-item label="库位">
          {{ adjustRow.locationName }}
        </el-descriptions-item>
        <el-descriptions-item label="物料">
          {{ adjustRow.skuName }}
        </el-descriptions-item>
        <el-descriptions-item
          label="当前库存"
          :span="2"
        >
          {{ formatQty(adjustRow.stockQty) }} {{ adjustRow.baseUnit }}
        </el-descriptions-item>
      </el-descriptions>
      <el-form label-width="100px">
        <el-form-item
          label="调整后数量"
          required
        >
          <el-input-number
            v-model="adjustForm.targetQty"
            :min="0"
            :step="1"
            :precision="3"
            controls-position="right"
          />
          <span class="muted small ml4">目标数量（非增减量）</span>
        </el-form-item>
        <el-form-item
          label="调整事由"
          required
        >
          <el-input
            v-model="adjustForm.reason"
            type="textarea"
            :rows="2"
            maxlength="2000"
            placeholder="如盘点差异补录（必填，记入调账单审计）"
          />
        </el-form-item>
      </el-form>
      <div class="muted small">
        差值自动生成 ADJUST 调账单并审批过账，立即生效；调整记录可在「库存流水」中追溯。
      </div>
      <template #footer>
        <el-button @click="adjustVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitAdjust"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 期初存量批量导入 -->
    <ImportDialog
      ref="importDialogRef"
      title="期初存量批量导入"
      tips="每行为一个库位+SKU 组合，仅允许当前无存量的组合（已有存量可在本面板「调整库存」中直接调整）；有效行将组装为一张期初入库单并自动过账入账（余额立即更新）。列：库位编码 / SKU码 / 数量 / 单价（单价可空）。"
      :upload-fn="importOpeningBalances"
      :template-fn="downloadOpeningImportTemplate"
      fallback-template-name="期初存量导入模板"
      @imported="pager.load"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import {
  adjustStockBalance,
  downloadOpeningImportTemplate,
  exportBalances,
  importOpeningBalances,
  pageBalances,
  pageWarnings,
  updateBalanceWarning
} from '@/api/item'
import { saveBlobFile } from '@/utils/download'
import { usePagedList } from '@/utils/usePagedList'
import { warningLevelMap } from '@/utils/statusMap'
import { fmtShanghai, formatQty } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import LocationSelect from '@/components/LocationSelect.vue'
import SkuSelect from '@/components/SkuSelect.vue'
import ImportDialog from '@/components/ImportDialog.vue'

const query = reactive({ locationId: null, skuId: null })
const warningOnly = ref(false)
const importDialogRef = ref(null)
const exporting = ref(false)

const pager = usePagedList((params) => {
  const common = {
    ...params,
    locationId: query.locationId || undefined,
    skuId: query.skuId || undefined
  }
  return warningOnly.value ? pageWarnings(common) : pageBalances(common)
})

const warningVisible = ref(false)
const submitting = ref(false)
const warningRow = ref(null)
const warningForm = reactive({ safeQty: 0, targetQty: 0 })

const adjustVisible = ref(false)
const adjustRow = ref(null)
const adjustForm = reactive({ targetQty: 0, reason: '' })

function openWarning(row) {
  warningRow.value = row
  warningForm.safeQty = Number(row.safeQty ?? 0)
  warningForm.targetQty = Number(row.targetQty ?? 0)
  warningVisible.value = true
}

async function submitWarning() {
  if (warningForm.safeQty === null || warningForm.safeQty === undefined) {
    ElMessage.warning('请填写安全库存')
    return
  }
  if (warningForm.targetQty === null || warningForm.targetQty === undefined) {
    ElMessage.warning('请填写补货目标')
    return
  }
  if (warningForm.targetQty < warningForm.safeQty) {
    ElMessage.warning('补货目标不能低于安全库存')
    return
  }
  submitting.value = true
  try {
    await updateBalanceWarning(warningRow.value.id, {
      safeQty: String(warningForm.safeQty),
      targetQty: String(warningForm.targetQty)
    })
    ElMessage.success('预警配置已保存')
    warningVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

function openAdjust(row) {
  adjustRow.value = row
  adjustForm.targetQty = Number(row.stockQty ?? 0)
  adjustForm.reason = ''
  adjustVisible.value = true
}

async function submitAdjust() {
  if (adjustForm.targetQty === null || adjustForm.targetQty === undefined) {
    ElMessage.warning('请填写调整后数量')
    return
  }
  if (Number(adjustForm.targetQty) === Number(adjustRow.value.stockQty)) {
    ElMessage.warning('调整后数量与当前库存一致，无需调整')
    return
  }
  if (!adjustForm.reason || !adjustForm.reason.trim()) {
    ElMessage.warning('请填写调整事由')
    return
  }
  submitting.value = true
  try {
    const documentId = await adjustStockBalance(adjustRow.value.id, {
      targetQty: String(adjustForm.targetQty),
      reason: adjustForm.reason.trim()
    })
    ElMessage.success(`库存已调整，调账单 ${documentId}`)
    adjustVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doExport(format) {
  exporting.value = true
  try {
    const file = await exportBalances({
      locationId: query.locationId || undefined,
      skuId: query.skuId || undefined,
      format
    })
    saveBlobFile(file, `物料存量导出.${format}`)
    ElMessage.success('导出成功，文件已开始下载')
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  pager.load()
})

// 页签切回时刷新列表（分类下拉为远程关键字搜索，打开时自动取最新数据）
function refresh() {
  pager.load()
}

defineExpose({ refresh })
</script>

<style scoped>
.mb8 {
  margin-bottom: 8px;
}
</style>
