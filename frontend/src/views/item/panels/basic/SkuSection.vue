<template>
  <div>
    <div class="toolbar">
      <div class="sku_section__search">
        <el-autocomplete
          v-model="skuCode"
          :fetch-suggestions="(q, cb) => suggestField(q, cb, 'skuCode')"
          placeholder="SKU 码"
          clearable
          @keyup.enter="pager.search"
          @clear="pager.search"
          @select="pager.search"
        />
      </div>
      <div class="sku_section__search">
        <el-autocomplete
          v-model="materialName"
          :fetch-suggestions="(q, cb) => suggestField(q, cb, 'materialName')"
          placeholder="物料名称"
          clearable
          @keyup.enter="pager.search"
          @clear="pager.search"
          @select="pager.search"
        />
      </div>
      <el-select
        v-model="materialType"
        clearable
        placeholder="全部类型"
        @change="pager.search"
      >
        <el-option
          label="全部类型"
          value=""
        />
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
        @change="pager.search"
      >
        <el-option
          label="全部状态"
          value=""
        />
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
      <el-button
        v-permission="'item:material:import'"
        @click="importDialogRef.open()"
      >
        批量导入
      </el-button>
      <el-dropdown
        v-permission="'item:material:export'"
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
          <CategorySelect
            v-model="form.categoryId"
            class="w240"
            placeholder="搜索分类"
          />
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
        SKU 码全局唯一；被余额 / 单据 / 申领 / 定额引用时禁止删除。
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

    <!-- 批量导入物料 -->
    <ImportDialog
      ref="importDialogRef"
      title="批量导入物料"
      tips="模板前 9 列为必填列（分类编码 / SKU 码 / 物料名称 / 规格型号 / 计量单位 / 数量精度 / 物料类型 / 危险品 / 高价值）；分类编码须已存在，SKU 码冲突的行跳过并记入失败明细。"
      :upload-fn="importSkus"
      :template-fn="downloadSkuImportTemplate"
      fallback-template-name="物料导入模板"
      @imported="pager.load"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import {
  createMaterialSku,
  deleteMaterialSku,
  downloadSkuImportTemplate,
  exportSkus,
  importSkus,
  pageSkus,
  updateMaterialSku,
  updateSkuStatus
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { materialTypeMap } from '@/utils/statusMap'
import { saveBlobFile } from '@/utils/download'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import CategorySelect from '@/components/CategorySelect.vue'

const skuCode = ref('')
const materialName = ref('')
const materialType = ref(null)
const enabled = ref(null)
const importDialogRef = ref(null)
const exporting = ref(false)

const pager = usePagedList((params) =>
  pageSkus({
    ...params,
    skuCode: skuCode.value || undefined,
    materialName: materialName.value || undefined,
    materialType: materialType.value || undefined,
    enabled: enabled.value || undefined
  })
)

/**
 * typeahead 联想：按输入片段查前 10 条 SKU，取对应字段去重映射为 { value }；
 * SKU 码与名称均为模糊匹配（与后端查询语义一致）。
 * 失败静默返回空（查询错误由拦截器统一提示，输入辅助不重复打扰）。
 */
async function suggestField(queryString, cb, field) {
  try {
    const params = { pageNum: 1, pageSize: 10 }
    if (queryString && queryString.trim()) {
      params[field] = queryString.trim()
    }
    const data = await pageSkus(params)
    const seen = new Set()
    const list = []
    for (const row of data.records || []) {
      const text = row[field]
      if (text && !seen.has(text)) {
        seen.add(text)
        list.push({ value: text })
      }
    }
    cb(list)
  } catch {
    cb([])
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

async function doExport(format) {
  exporting.value = true
  try {
    const file = await exportSkus({
      skuCode: skuCode.value || undefined,
      materialName: materialName.value || undefined,
      materialType: materialType.value || undefined,
      enabled: enabled.value || undefined,
      format
    })
    saveBlobFile(file, `物料档案导出.${format}`)
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

defineExpose({ refresh: pager.load })
</script>

<style scoped>
/* 搜索框外套固定宽度容器：el-autocomplete 会把 class 透传到内部 el-input，
   scoped 选择器命不中，故宽度控制在 wrapper 上 */
.sku_section__search {
  width: 180px;
  flex: none;
}
</style>
