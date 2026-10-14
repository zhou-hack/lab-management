<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        class="w220"
        placeholder="SKU 码 / 名称 / 规格"
        @change="pager.search"
      />
      <el-select
        v-model="categoryId"
        clearable
        filterable
        placeholder="全部分类"
        class="w200"
        @change="pager.search"
      >
        <el-option
          v-for="c in categories"
          :key="c.id"
          :label="c.categoryName"
          :value="c.id"
        />
      </el-select>
      <el-select
        v-model="materialType"
        clearable
        placeholder="全部类型"
        class="w140"
        @change="pager.search"
      >
        <el-option
          label="耗材"
          value="CONSUMABLE"
        />
        <el-option
          label="可回收"
          value="RETURNABLE"
        />
      </el-select>
      <el-select
        v-model="enabled"
        clearable
        placeholder="全部状态"
        class="w140"
        @change="pager.search"
      >
        <el-option
          label="启用"
          value="true"
        />
        <el-option
          label="停用"
          value="false"
        />
      </el-select>
      <el-button
        v-permission="'item:material:create'"
        type="primary"
        @click="openCreate"
      >
        新增物料
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
        prop="skuCode"
        label="SKU 码"
        width="170"
        show-overflow-tooltip
      />
      <el-table-column
        prop="materialName"
        label="物料名称"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        prop="specification"
        label="规格型号"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        prop="categoryName"
        label="分类"
        width="120"
        show-overflow-tooltip
      />
      <el-table-column
        prop="baseUnit"
        label="单位"
        width="70"
        align="center"
      />
      <el-table-column
        label="类型"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.materialType"
            :map="materialTypeMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="标记"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            v-if="row.isHazardous"
            type="danger"
            size="small"
          >
            危
          </el-tag>
          <el-tag
            v-if="row.isValuable"
            type="warning"
            size="small"
            class="ml4"
          >
            贵
          </el-tag>
          <span
            v-if="!row.isHazardous && !row.isValuable"
            class="muted"
          >-</span>
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="80"
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
            v-permission="'item:material:update'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'item:material:enable'"
            link
            :type="row.enabled ? 'warning' : 'success'"
            @click="doToggle(row)"
          >
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-permission="'item:material:delete'"
            link
            type="danger"
            @click="doDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 新增 / 编辑物料 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑物料' : '新增物料'"
      width="640px"
    >
      <el-form label-width="110px">
        <el-form-item
          label="所属分类"
          required
        >
          <el-select
            v-model="form.categoryId"
            filterable
            class="w240"
            placeholder="选择分类"
          >
            <el-option
              v-for="c in categories"
              :key="c.id"
              :label="`${c.categoryCode} · ${c.categoryName}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="SKU 码"
          required
        >
          <el-input
            v-model="form.skuCode"
            class="w240"
            maxlength="64"
            placeholder="如 SKU-ETHANOL-500ML（删除后不可复用）"
          />
        </el-form-item>
        <el-form-item
          label="物料名称"
          required
        >
          <el-input
            v-model="form.materialName"
            class="w240"
            maxlength="128"
            placeholder="如 无水乙醇 500mL"
          />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input
            v-model="form.specification"
            class="w240"
            maxlength="500"
            placeholder="如 AR，500mL/瓶"
          />
        </el-form-item>
        <el-form-item
          label="计量单位"
          required
        >
          <el-input
            v-model="form.baseUnit"
            class="w160"
            maxlength="16"
            placeholder="如 瓶"
          />
        </el-form-item>
        <el-form-item
          label="数量精度"
          required
        >
          <el-input-number
            v-model="form.quantityScale"
            :min="0"
            :max="3"
            :step="1"
            controls-position="right"
          />
          <span class="muted small ml4">小数位数 0-3，整数单位填 0</span>
        </el-form-item>
        <el-form-item
          label="物料类型"
          required
        >
          <el-select
            v-model="form.materialType"
            class="w240"
          >
            <el-option
              label="耗材 CONSUMABLE"
              value="CONSUMABLE"
            />
            <el-option
              label="可回收 RETURNABLE"
              value="RETURNABLE"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="危险品">
          <el-switch v-model="form.isHazardous" />
        </el-form-item>
        <el-form-item label="高价值">
          <el-switch v-model="form.isValuable" />
        </el-form-item>
      </el-form>
      <div class="muted small">
        SKU 码全局唯一；被余额 / 单据 / 申领 / 定额 / 盘点引用时禁止删除。
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitForm"
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
import {
  createMaterialSku,
  deleteMaterialSku,
  listEnabledCategories,
  pageSkus,
  updateMaterialSku,
  updateSkuStatus
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { materialTypeMap } from '@/utils/statusMap'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'

const keyword = ref('')
const categoryId = ref(null)
const materialType = ref(null)
const enabled = ref(null)

const pager = usePagedList((params) =>
  pageSkus({
    ...params,
    keyword: keyword.value || undefined,
    categoryId: categoryId.value || undefined,
    materialType: materialType.value || undefined,
    enabled: enabled.value === null || enabled.value === undefined ? undefined : enabled.value
  })
)

const categories = ref([])

async function loadCategories() {
  try {
    categories.value = await listEnabledCategories()
  } catch {
    // 失败已由全局拦截器提示
  }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const form = reactive(emptyForm())

function emptyForm() {
  return {
    id: '',
    categoryId: null,
    skuCode: '',
    materialName: '',
    specification: '',
    baseUnit: '',
    quantityScale: 0,
    materialType: 'CONSUMABLE',
    isHazardous: false,
    isValuable: false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    id: row.id,
    categoryId: row.categoryId,
    skuCode: row.skuCode,
    materialName: row.materialName,
    specification: row.specification || '',
    baseUnit: row.baseUnit,
    quantityScale: row.quantityScale,
    materialType: row.materialType,
    isHazardous: !!row.isHazardous,
    isValuable: !!row.isValuable
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.categoryId) {
    ElMessage.warning('请选择所属分类')
    return
  }
  if (!form.skuCode) {
    ElMessage.warning('SKU 码不能为空')
    return
  }
  if (!form.materialName) {
    ElMessage.warning('物料名称不能为空')
    return
  }
  if (!form.baseUnit) {
    ElMessage.warning('计量单位不能为空')
    return
  }
  if (form.quantityScale === null || form.quantityScale === undefined) {
    ElMessage.warning('请填写数量精度')
    return
  }
  submitting.value = true
  try {
    const payload = {
      categoryId: form.categoryId,
      skuCode: form.skuCode,
      materialName: form.materialName,
      specification: form.specification || undefined,
      baseUnit: form.baseUnit,
      quantityScale: form.quantityScale,
      materialType: form.materialType,
      isHazardous: form.isHazardous,
      isValuable: form.isValuable
    }
    if (editing.value) {
      await updateMaterialSku({ id: form.id, ...payload })
      ElMessage.success('物料已更新')
    } else {
      await createMaterialSku(payload)
      ElMessage.success('物料已创建')
    }
    dialogVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doToggle(row) {
  const target = !row.enabled
  const ok = await confirmAction(
    target ? `确认启用物料「${row.materialName}」？` : `确认停用物料「${row.materialName}」？停用后单据与申领不可选用。`
  )
  if (!ok) return
  try {
    await updateSkuStatus(row.id, target)
    ElMessage.success(target ? '已启用' : '已停用')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doDelete(row) {
  const ok = await confirmAction(
    `确认删除物料「${row.materialName}」？逻辑删除后 SKU 码不可复用；被业务数据引用时将被拒绝。`,
    '删除确认'
  )
  if (!ok) return
  try {
    await deleteMaterialSku(row.id)
    ElMessage.success('已删除')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

onMounted(() => {
  loadCategories()
  pager.load()
})

defineExpose({ refresh: pager.load })
</script>
