<template>
  <div>
    <div class="toolbar">
      <LocationSelect
        v-model="query.locationId"
        placeholder="全部库位"
        @change="pager.search"
      />
      <SkuSelect
        v-model="query.skuId"
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
        v-if="!warningOnly"
        label="移动均价"
        width="110"
        align="right"
      >
        <template #default="{ row }">
          {{ formatQty(row.movingAveragePrice) }}
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
        width="110"
        fixed="right"
      >
        <template #default="{ row }">
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageBalances, pageWarnings, updateBalanceWarning } from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { warningLevelMap } from '@/utils/statusMap'
import { fmtShanghai, formatQty } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import LocationSelect from '@/components/LocationSelect.vue'
import SkuSelect from '@/components/SkuSelect.vue'

const query = reactive({ locationId: null, skuId: null })
const warningOnly = ref(false)

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

onMounted(pager.load)

defineExpose({ refresh: pager.load })
</script>

<style scoped>
.mb8 {
  margin-bottom: 8px;
}
</style>
