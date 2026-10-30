<template>
  <el-dialog
    :model-value="modelValue"
    title="导入物料清单"
    width="min(1000px, 95vw)"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="resetAll"
  >
    <el-alert
      class="asset_import__notice"
      type="info"
      :closable="false"
      show-icon
      title="优先按类别列识别耗材和设备，无类别时结合名称与单位判断。耗材按数量入库，设备逐台建档；无法判断的行需补充类别。"
    />

    <template v-if="result">
      <el-result
        icon="success"
        title="物料清单处理完成"
        :sub-title="result.documentNo ? `入库单号：${result.documentNo}` : '没有新增物料或库存，未生成重复入库单'"
      />
      <div class="asset_import__summary">
        <div>新建物料<strong>{{ result.createdMaterialCount }}</strong></div>
        <div>新增资产<strong>{{ result.importedAssetCount }}</strong></div>
        <div>耗材入库数量<strong>{{ result.importedConsumableQuantity }}</strong></div>
        <div>跳过已有资产<strong>{{ result.skippedAssetCount }}</strong></div>
      </div>
    </template>

    <template v-else>
      <div class="asset_import__form">
        <div
          v-if="selectedFile"
          class="asset_import__location"
        >
          <span class="asset_import__label">导入章节</span>
          <el-select
            v-model="section"
            :disabled="previewing || importing"
            @change="onSectionChange"
          >
            <el-option
              label="第一部分：电工电子实训室"
              :value="1"
            />
            <el-option
              label="第二部分：计算机网络实训室"
              :value="2"
            />
            <el-option
              label="第三部分：3D数字孪生平台"
              :value="3"
            />
            <el-option
              label="全部章节"
              :value="0"
            />
          </el-select>
        </div>
        <div class="asset_import__location">
          <span class="asset_import__label">导入库位</span>
          <item-location-select
            v-model="locationId"
            :clearable="false"
            :disabled="previewing || importing"
            placeholder="请选择A703库位"
            @change="onLocationChange"
          />
        </div>
        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="false"
          :limit="1"
          :disabled="previewing || importing"
          :on-change="onFileChange"
          :on-exceed="onExceed"
          accept=".xlsx,.xls"
        >
          <el-icon class="el-icon--upload">
            <UploadFilled />
          </el-icon>
          <div class="el-upload__text">
            将物料清单拖到此处，或<em>点击选择</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持 .xls / .xlsx，文件不超过 10MB
            </div>
          </template>
        </el-upload>
      </div>

      <template v-if="preview">
        <el-alert
          class="asset_import__recognized"
          type="success"
          :closable="false"
          :title="`已识别工作表：${preview.sheetName}；请核对类别、物料名称、规格、单位和数量`"
        />
        <div class="asset_import__summary">
          <div>物料<strong>{{ preview.materialCount }}</strong></div>
          <div>设备<strong>{{ preview.assetCount }}</strong></div>
          <div>耗材数量<strong>{{ preview.consumableQuantity }}</strong></div>
          <div>已存在资产<strong>{{ preview.existingAssetCount }}</strong></div>
        </div>
        <el-alert
          v-if="preview.issues.length"
          class="asset_import__issues"
          type="error"
          :closable="false"
          title="存在错误，修正文件后重新预览；确认导入不会写入任何数据"
        />
        <el-table
          v-if="preview.issues.length"
          :data="preview.issues"
          height="220"
          size="small"
        >
          <el-table-column
            prop="rowNum"
            label="Excel行号"
            width="100"
          />
          <el-table-column
            prop="message"
            label="错误原因"
            min-width="360"
            show-overflow-tooltip
          />
        </el-table>
        <el-table
          v-else
          :data="preview.rows"
          height="360"
          size="small"
        >
          <el-table-column
            prop="rowNum"
            label="行号"
            width="64"
          />
          <el-table-column
            prop="materialName"
            label="物料名称"
            min-width="130"
            show-overflow-tooltip
          />
          <el-table-column
            label="类别"
            width="76"
          >
            <template #default="{ row }">
              {{ row.materialType === 'CONSUMABLE' ? '耗材' : '设备' }}
            </template>
          </el-table-column>
          <el-table-column
            label="品牌 / 型号"
            min-width="170"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.specification || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="baseUnit"
            label="单位"
            width="64"
          />
          <el-table-column
            prop="quantity"
            label="数量"
            width="76"
            align="right"
          />
          <el-table-column
            prop="skuCode"
            label="物料编码"
            min-width="120"
          />
          <el-table-column
            label="已有 / 待导入"
            width="110"
            align="center"
          >
            <template #default="{ row }">
              <span v-if="row.materialType === 'CONSUMABLE'">—</span>
              <span v-else>{{ row.existingAssetCount }} / {{ row.quantity - row.existingAssetCount }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </template>

    <template #footer>
      <template v-if="result">
        <el-button @click="resetAll">
          继续导入
        </el-button>
        <el-button
          type="primary"
          @click="emit('update:modelValue', false)"
        >
          完成
        </el-button>
      </template>
      <template v-else-if="preview">
        <el-button @click="preview = null">
          重新选择
        </el-button>
        <el-button
          type="primary"
          :disabled="!preview.canImport"
          :loading="importing"
          @click="confirmImport"
        >
          确认导入
        </el-button>
      </template>
      <template v-else>
        <el-button @click="emit('update:modelValue', false)">
          取消
        </el-button>
        <el-button
          type="primary"
          :disabled="!selectedFile || !locationId"
          :loading="previewing"
          @click="loadPreview"
        >
          解析预览
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import { importAssets, previewAssetImport } from '@/api/item/stock'

defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'success'])

const uploadRef = ref(null)
const selectedFile = ref(null)
const section = ref(1)
const locationId = ref('')
const preview = ref(null)
const result = ref(null)
const previewing = ref(false)
const importing = ref(false)
const MAX_SIZE = 10 * 1024 * 1024

function validateFile(file) {
  const name = file?.name?.toLowerCase() || ''
  if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) return false
  return file.size <= MAX_SIZE
}

function onFileChange(uploadFile) {
  if (uploadFile.status === 'ready' && validateFile(uploadFile.raw)) {
    selectedFile.value = uploadFile.raw
    preview.value = null
    result.value = null
  } else {
    selectedFile.value = null
    uploadRef.value?.clearFiles()
  }
}

function onExceed(files) {
  uploadRef.value?.clearFiles()
  if (files[0] && validateFile(files[0])) uploadRef.value?.handleStart(files[0])
}

function onSectionChange() {
  preview.value = null
  result.value = null
}

function onLocationChange() {
  preview.value = null
  result.value = null
}

async function loadPreview() {
  previewing.value = true
  try {
    preview.value = await previewAssetImport(selectedFile.value, locationId.value, section.value)
  } catch {
    // 请求层已展示文件或权限错误
  } finally {
    previewing.value = false
  }
}

async function confirmImport() {
  importing.value = true
  try {
    result.value = await importAssets(selectedFile.value, locationId.value, section.value)
    if (result.value.importedAssetCount > 0 || result.value.importedConsumableQuantity > 0) {
      emit('success', result.value)
    }
  } catch {
    // 请求层已展示校验或导入错误，保留预览供重试
  } finally {
    importing.value = false
  }
}

function resetAll() {
  selectedFile.value = null
  section.value = 1
  locationId.value = ''
  preview.value = null
  result.value = null
  previewing.value = false
  importing.value = false
  uploadRef.value?.clearFiles()
}
</script>

<style scoped>
.asset_import__notice {
  margin-bottom: 16px;
}

.asset_import__location {
  display: grid;
  grid-template-columns: 90px minmax(240px, 1fr);
  align-items: center;
  margin-bottom: 14px;
}

.asset_import__label {
  color: var(--g2);
  font-weight: var(--fw-medium);
}

.asset_import__summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin: 16px 0;
}

.asset_import__summary > div {
  padding: 14px;
  color: var(--g3);
  text-align: center;
  background: var(--surface-sunken);
  border-radius: var(--r-md);
}

.asset_import__summary strong {
  display: block;
  margin-top: 4px;
  color: var(--g1);
  font-size: 26px;
}

.asset_import__issues {
  margin-bottom: 10px;
}
</style>
